#!/usr/bin/env bash
#
# start.sh - Spin up the Certificate Discovery Platform (Spring Boot backend +
#            Vite/React frontend) in one command.
#
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend/frontend"
LOG_DIR="$ROOT_DIR/logs"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"   # seconds to wait for backend health

RUN_BACKEND=true
RUN_FRONTEND=true
SKIP_INSTALL=false
CLEAR_DB=false

BACKEND_PID=""
FRONTEND_PID=""
TAIL_PID=""
SHUTTING_DOWN=false

# ----------------------------------------------------------------- output ---
if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_BLUE=$'\033[34m'; C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'; C_RED=$'\033[31m'; C_DIM=$'\033[2m'
else
  C_RESET=""; C_BLUE=""; C_GREEN=""; C_YELLOW=""; C_RED=""; C_DIM=""
fi

info()  { printf '%s[start]%s %s\n' "$C_BLUE"   "$C_RESET" "$*"; }
ok()    { printf '%s[ ok  ]%s %s\n' "$C_GREEN"  "$C_RESET" "$*"; }
warn()  { printf '%s[warn ]%s %s\n' "$C_YELLOW" "$C_RESET" "$*"; }
die()   { printf '%s[error]%s %s\n' "$C_RED"    "$C_RESET" "$*" >&2; exit 1; }

usage() {
  cat <<'USAGE'
start.sh - start the Certificate Discovery Platform (Spring Boot backend + Vite/React frontend).

Usage:
  ./start.sh                      start both on the dev profile, keeping existing data
  ./start.sh --profile qa         run the qa profile (real cloud credentials, own database)
  ./start.sh --clear-db           wipe this profile's database first, then start
  ./start.sh --profile qa --clear-db
  ./start.sh --backend-only
  ./start.sh --frontend-only
  ./start.sh --skip-install       don't run npm install even if node_modules is stale
  ./start.sh --help

Data is kept between runs by default; --clear-db (alias --fresh) is the only thing
that deletes it, and it only ever touches the database of the selected profile.

Profiles:
  dev  every cloud provider simulated, database backend/certplatform-dev.db
  qa   AWS calls go to real AWS,       database backend/certplatform-qa.db

Env overrides:
  BACKEND_PORT (8080)  FRONTEND_PORT (5173)  SPRING_PROFILES_ACTIVE (dev)
  HEALTH_TIMEOUT (180)
  CERTPLATFORM_SECRET_KEY   required by qa; encrypts stored cloud credentials
  AWS_ENDPOINT_OVERRIDE     point qa at LocalStack instead of real AWS
USAGE
}

# ------------------------------------------------------------------- args ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-only)  RUN_FRONTEND=false ;;
    --frontend-only) RUN_BACKEND=false ;;
    --skip-install)  SKIP_INSTALL=true ;;
    --clear-db|--fresh) CLEAR_DB=true ;;
    --profile)       [[ $# -ge 2 ]] || die "--profile needs a value (dev or qa)"
                     PROFILE="$2"; shift ;;
    --profile=*)     PROFILE="${1#*=}" ;;
    -h|--help)       usage; exit 0 ;;
    *)               die "Unknown option: $1 (try --help)" ;;
  esac
  shift
done

[[ -n "$PROFILE" ]] || die "--profile needs a value (dev or qa)"
export SPRING_PROFILES_ACTIVE="$PROFILE"

# Each profile keeps its own SQLite file, named to match application-<profile>.yml.
DB_FILE="$BACKEND_DIR/certplatform-${PROFILE}.db"

# -------------------------------------------------------------- utilities ---
# Deletes the selected profile's database. SQLite also leaves -wal/-shm/-journal
# sidecar files behind; leaving those would resurrect data we just deleted.
clear_database() {
  local removed=false
  local f
  for f in "$DB_FILE" "$DB_FILE-wal" "$DB_FILE-shm" "$DB_FILE-journal"; do
    if [[ -e "$f" ]]; then
      rm -f "$f"
      info "  removed $(basename "$f")"
      removed=true
    fi
  done
  if $removed; then
    ok "Cleared the $PROFILE database; Flyway will recreate the schema on startup."
  else
    info "No database at $DB_FILE yet - nothing to clear."
  fi
}

port_in_use() {
  # Returns 0 if something is already listening on $1.
  local port="$1"
  (exec 3<>"/dev/tcp/127.0.0.1/$port") >/dev/null 2>&1 && { exec 3<&-; exec 3>&-; return 0; }
  return 1
}

require_free_port() {
  local port="$1" what="$2"
  if port_in_use "$port"; then
    die "Port $port ($what) is already in use. Stop that process, or re-run with a different port:
       ${what^^}_PORT=<port> ./start.sh"
  fi
}

# Start a command in its own process group so we can reliably kill its children
# (gradle forks a separate JVM for bootRun; vite forks esbuild workers).
start_bg() {
  local dir="$1" logfile="$2"; shift 2
  if command -v setsid >/dev/null 2>&1; then
    ( cd "$dir" && exec setsid "$@" ) >"$logfile" 2>&1 &
  else
    ( cd "$dir" && exec "$@" ) >"$logfile" 2>&1 &
  fi
  printf '%s' "$!"
}

stop_pid() {
  local pid="$1" name="$2"
  [[ -n "$pid" ]] || return 0
  kill -0 "$pid" 2>/dev/null || return 0
  info "Stopping $name (pid $pid)..."
  # Kill the whole process group when we own one, otherwise just the process.
  kill -TERM "-$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 0.5
  done
  warn "$name did not exit gracefully; sending SIGKILL"
  kill -KILL "-$pid" 2>/dev/null || kill -KILL "$pid" 2>/dev/null || true
}

cleanup() {
  trap - EXIT INT TERM
  SHUTTING_DOWN=true
  echo
  if [[ -n "$TAIL_PID" ]]; then kill "$TAIL_PID" 2>/dev/null || true; fi
  stop_pid "$FRONTEND_PID" "frontend"
  stop_pid "$BACKEND_PID"  "backend"
  ok "All services stopped."
}
trap cleanup EXIT INT TERM

# --------------------------------------------------------------- preflight ---
mkdir -p "$LOG_DIR"

if $RUN_BACKEND; then
  [[ -x "$BACKEND_DIR/gradlew" ]] || die "Missing $BACKEND_DIR/gradlew"
  command -v java >/dev/null 2>&1 || die "java not found on PATH (JDK 21 required)"
  [[ -f "$BACKEND_DIR/src/main/resources/application-${PROFILE}.yml" ]] \
    || die "No application-${PROFILE}.yml in $BACKEND_DIR/src/main/resources (known profiles: dev, qa)"
  require_free_port "$BACKEND_PORT" "backend"

  # qa holds real cloud credentials, and the app refuses to start without a key to
  # encrypt them with. Fail here with instructions rather than on a Spring stack trace.
  if [[ "$PROFILE" == "qa" && -z "${CERTPLATFORM_SECRET_KEY:-}" ]]; then
    die "The qa profile stores real cloud credentials and needs an encryption key.
       Generate one, then keep it safe - stored credentials cannot be read back without it:
         export CERTPLATFORM_SECRET_KEY=\"\$(openssl rand -base64 32)\""
  fi

  if $CLEAR_DB; then
    info "Clearing the $PROFILE database at $DB_FILE"
    clear_database
  fi
fi

if $RUN_FRONTEND; then
  [[ -f "$FRONTEND_DIR/package.json" ]] || die "Missing $FRONTEND_DIR/package.json"
  command -v npm >/dev/null 2>&1 || die "npm not found on PATH (Node.js >= 18 required)"
  require_free_port "$FRONTEND_PORT" "frontend"

  if ! $SKIP_INSTALL; then
    if [[ ! -d "$FRONTEND_DIR/node_modules" ||
          "$FRONTEND_DIR/package-lock.json" -nt "$FRONTEND_DIR/node_modules" ]]; then
      info "Installing frontend dependencies (npm ci)..."
      ( cd "$FRONTEND_DIR" && { npm ci || npm install; } ) \
        || die "Frontend dependency install failed"
      touch "$FRONTEND_DIR/node_modules"
    fi
  fi
fi

# ----------------------------------------------------------------- backend ---
if $RUN_BACKEND; then
  BACKEND_LOG="$LOG_DIR/backend.log"
  info "Starting backend on http://localhost:$BACKEND_PORT (profile: $PROFILE)"
  info "  log: $BACKEND_LOG"
  info "  database: $DB_FILE$($CLEAR_DB && echo " (cleared)" || echo " (existing data kept)")"
  BACKEND_PID="$(start_bg "$BACKEND_DIR" "$BACKEND_LOG" \
    ./gradlew bootRun --console=plain "--args=--server.port=$BACKEND_PORT")"

  info "Waiting for backend to become healthy (up to ${HEALTH_TIMEOUT}s)..."
  deadline=$(( SECONDS + HEALTH_TIMEOUT ))
  until port_in_use "$BACKEND_PORT"; do
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      warn "Backend process exited. Last 40 log lines:"
      tail -n 40 "$BACKEND_LOG" >&2 || true
      die "Backend failed to start."
    fi
    (( SECONDS < deadline )) || die "Backend did not start within ${HEALTH_TIMEOUT}s (see $BACKEND_LOG)"
    sleep 1
  done
  if command -v curl >/dev/null 2>&1; then
    status="$(curl -fsS "http://localhost:$BACKEND_PORT/actuator/health" 2>/dev/null || true)"
    [[ -n "$status" ]] && info "  /actuator/health -> $status"
  fi
  ok "Backend up: http://localhost:$BACKEND_PORT  (docs: /swagger-ui.html)"
fi

# ---------------------------------------------------------------- frontend ---
if $RUN_FRONTEND; then
  FRONTEND_LOG="$LOG_DIR/frontend.log"
  info "Starting frontend on http://localhost:$FRONTEND_PORT"
  info "  log: $FRONTEND_LOG"
  export BACKEND_PROXY_TARGET="http://localhost:$BACKEND_PORT"   # read by vite.config.ts
  FRONTEND_PID="$(start_bg "$FRONTEND_DIR" "$FRONTEND_LOG" \
    npm run dev -- --port "$FRONTEND_PORT" --strictPort)"

  deadline=$(( SECONDS + 90 ))
  until port_in_use "$FRONTEND_PORT"; do
    if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
      warn "Frontend process exited. Last 40 log lines:"
      tail -n 40 "$FRONTEND_LOG" >&2 || true
      die "Frontend failed to start."
    fi
    (( SECONDS < deadline )) || die "Frontend did not start within 90s (see $FRONTEND_LOG)"
    sleep 1
  done
  ok "Frontend up: http://localhost:$FRONTEND_PORT  (proxies /api -> :$BACKEND_PORT)"
fi

# --------------------------------------------------------------- run loop ---
echo
ok "Stack is running. Press Ctrl+C to stop everything."
printf '%s' "$C_DIM"
if $RUN_BACKEND;  then echo "  backend  http://localhost:$BACKEND_PORT"; fi
if $RUN_FRONTEND; then echo "  frontend http://localhost:$FRONTEND_PORT"; fi
printf '%s\n' "$C_RESET"

logs=()
if $RUN_BACKEND;  then logs+=("$LOG_DIR/backend.log"); fi
if $RUN_FRONTEND; then logs+=("$LOG_DIR/frontend.log"); fi
tail -n 0 -F "${logs[@]}" &
TAIL_PID=$!

# Exit as soon as either service dies, so we never leave a half-running stack.
while ! $SHUTTING_DOWN; do
  if [[ -n "$BACKEND_PID" ]] && ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    warn "Backend exited."; break
  fi
  if [[ -n "$FRONTEND_PID" ]] && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    warn "Frontend exited."; break
  fi
  sleep 2
done
