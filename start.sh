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
KILL_PORTS=true

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
  ./start.sh --no-kill-ports      fail instead of killing whatever holds the ports
  ./start.sh --help

Ports 8080 (backend) and 5173 (frontend) are freed automatically: anything still
listening on them - usually a bootRun or vite left over from a previous run - is
sent SIGTERM, then SIGKILL if it ignores that. Use --no-kill-ports to opt out.

Data is kept between runs by default; --clear-db (alias --fresh) is the only thing
that deletes it, and it only ever touches the database of the selected profile.

Profiles:
  dev  every cloud provider simulated, database backend/certplatform-dev.db
  qa   AWS calls go to real AWS,       database backend/certplatform-qa.db

Env overrides:
  BACKEND_PORT (8080)  FRONTEND_PORT (5173)  SPRING_PROFILES_ACTIVE (dev)
  HEALTH_TIMEOUT (180)
  CERTPLATFORM_SECRET_KEY   required by qa; encrypts stored cloud credentials
  AWS_PROFILE               identity the qa profile assumes IAM roles with
  AWS_ENDPOINT_OVERRIDE     point qa at LocalStack instead of real AWS

Before running qa, check the AWS identity this shell will use:
  aws sts get-caller-identity --profile qa && export AWS_PROFILE=qa
It needs sts:AssumeRole on the account's role, and that role must trust it.
Running on localhost does not keep the calls local - see README.md.
USAGE
}

# ------------------------------------------------------------------- args ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    --backend-only)  RUN_FRONTEND=false ;;
    --frontend-only) RUN_BACKEND=false ;;
    --skip-install)  SKIP_INSTALL=true ;;
    --clear-db|--fresh) CLEAR_DB=true ;;
    --no-kill-ports) KILL_PORTS=false ;;
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

# PIDs listening on $1, one per line. Tries the tools in order of how precisely
# they report listeners; every box has at least one of them.
listener_pids() {
  local port="$1" pids=""
  if command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -t -i "TCP:$port" -s TCP:LISTEN 2>/dev/null || true)"
  fi
  if [[ -z "$pids" ]] && command -v ss >/dev/null 2>&1; then
    pids="$(ss -lntpH "sport = :$port" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | sort -u || true)"
  fi
  if [[ -z "$pids" ]] && command -v fuser >/dev/null 2>&1; then
    pids="$(fuser -n tcp "$port" 2>/dev/null | tr -s ' ' '\n' || true)"
  fi
  # Never target ourselves or our own process group - that would kill this script.
  local pid
  for pid in $pids; do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    (( pid == $$ || pid == 1 )) && continue
    printf '%s\n' "$pid"
  done | sort -u
}

# Frees $1 by terminating whatever listens on it: SIGTERM first, SIGKILL for
# anything still holding the port a few seconds later.
kill_port() {
  local port="$1" what="$2"
  local pids
  pids="$(listener_pids "$port")"

  if [[ -z "$pids" ]]; then
    # Something answers on the port but we cannot see the owner: another user's
    # process, or a container publishing it. Killing is not an option there.
    die "Port $port ($what) is in use by a process we cannot identify (another user,
       or a container publishing the port). Stop it, or re-run with a different port:
       ${what^^}_PORT=<port> ./start.sh"
  fi

  warn "Port $port ($what) is in use by pid(s) $(tr '\n' ' ' <<<"$pids" | sed 's/ $//'); terminating."
  local pid
  for pid in $pids; do
    ps -o pid=,command= -p "$pid" 2>/dev/null | sed 's/^ */  killing /' || true
    kill -TERM "$pid" 2>/dev/null || true
  done

  for _ in $(seq 1 20); do
    port_in_use "$port" || { ok "Port $port is free."; return 0; }
    sleep 0.5
  done

  warn "Port $port still busy after SIGTERM; sending SIGKILL."
  for pid in $(listener_pids "$port"); do
    kill -KILL "$pid" 2>/dev/null || true
  done

  for _ in $(seq 1 10); do
    port_in_use "$port" || { ok "Port $port is free."; return 0; }
    sleep 0.5
  done
  die "Could not free port $port ($what). Stop the process by hand, or re-run with:
       ${what^^}_PORT=<port> ./start.sh"
}

require_free_port() {
  local port="$1" what="$2"
  port_in_use "$port" || return 0
  if $KILL_PORTS; then
    kill_port "$port" "$what"
  else
    die "Port $port ($what) is already in use. Stop that process, re-run without
       --no-kill-ports to have start.sh free it, or use a different port:
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

  # qa assumes IAM roles with whatever the AWS SDK's default credential chain finds.
  # Warn rather than die: env credentials, an SSO session or LocalStack are all valid
  # ways to run without AWS_PROFILE set.
  if [[ "$PROFILE" == "qa" && -z "${AWS_PROFILE:-}" && -z "${AWS_ACCESS_KEY_ID:-}" ]]; then
    warn "No AWS_PROFILE or AWS_ACCESS_KEY_ID exported. IAM_ROLE accounts assume their role
       with the SDK's default credential chain, which will have nothing to sign with.
       Check the identity first, then export it:
         aws sts get-caller-identity --profile qa
         export AWS_PROFILE=qa"
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
