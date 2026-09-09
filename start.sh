#!/usr/bin/env bash
#
# start.sh - Spin up the Certificate Discovery Platform (Spring Boot backend +
#            Vite/React frontend) in one command.
#
# Two modes, selected with --mode:
#   native (default)  no Docker at all - Gradle runs the backend, Vite the frontend
#   docker            the backend runs as a container from backend/docker-compose.yml
# The frontend always runs on the host through npm; there is no frontend image.
#
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend/frontend"
LOG_DIR="$ROOT_DIR/logs"
BACKEND_COMPOSE="$BACKEND_DIR/docker-compose.yml"
BACKEND_COMPOSE_AWS="$BACKEND_DIR/docker-compose.aws.yml"
# Matches container_name / the volume name in backend/docker-compose.yml.
BACKEND_CONTAINER="certplatform-backend"
BACKEND_VOLUME="certplatform-data"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
MODE="${START_MODE:-native}"              # native (no Docker) or docker
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"   # seconds to wait for backend health

RUN_BACKEND=true
RUN_FRONTEND=true
SKIP_INSTALL=false
CLEAR_DB=false
KILL_PORTS=true

BACKEND_PID=""
FRONTEND_PID=""
TAIL_PID=""
DOCKER_LOGS_PID=""
COMPOSE=()                # docker compose invocation, filled in by resolve_compose
CONTAINER_STARTED=false
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
  ./start.sh --mode docker        run the backend as a container instead of via Gradle
  ./start.sh --backend-only
  ./start.sh --frontend-only
  ./start.sh --skip-install       don't run npm install even if node_modules is stale
  ./start.sh --no-kill-ports      fail instead of killing whatever holds the ports
  ./start.sh --help

Modes:
  native (default)  Docker is never touched: ./gradlew bootRun serves the backend and
                    npm run dev the frontend. Needs a JDK 21 and Node.js >= 18, nothing
                    else - the database is a plain SQLite file in backend/.
  docker            The backend is built and run as a container through
                    backend/docker-compose.yml, with its database on the
                    certplatform-data volume. The frontend still runs on the host with
                    npm, so Node.js is required in both modes.
  Aliases: --docker = --mode docker;  --no-docker / --native / --local = --mode native.

Ports 8080 (backend) and 5173 (frontend) are freed automatically: anything still
listening on them - usually a bootRun or vite left over from a previous run - is
sent SIGTERM, then SIGKILL if it ignores that. Use --no-kill-ports to opt out.
In docker mode a backend container left over from an earlier run is taken down first
whatever --no-kill-ports says, because it holds both the port and the container name.

Data is kept between runs by default; --clear-db (alias --fresh) is the only thing
that deletes it. In native mode it only ever touches the database of the selected
profile; in docker mode it removes the certplatform-data volume, which holds the
database of every profile that has run in a container.

Profiles:
  dev  every cloud provider simulated, database backend/certplatform-dev.db
  qa   AWS calls go to real AWS,       database backend/certplatform-qa.db

Env overrides:
  BACKEND_PORT (8080)  FRONTEND_PORT (5173)  SPRING_PROFILES_ACTIVE (dev)
  HEALTH_TIMEOUT (180)  START_MODE (native)
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
    --mode)          [[ $# -ge 2 ]] || die "--mode needs a value (native or docker)"
                     MODE="$2"; shift ;;
    --mode=*)        MODE="${1#*=}" ;;
    --docker)        MODE=docker ;;
    --no-docker|--native|--local) MODE=native ;;
    -h|--help)       usage; exit 0 ;;
    *)               die "Unknown option: $1 (try --help)" ;;
  esac
  shift
done

[[ -n "$PROFILE" ]] || die "--profile needs a value (dev or qa)"
export SPRING_PROFILES_ACTIVE="$PROFILE"

case "$MODE" in
  native|docker) ;;
  *) die "Unknown mode: $MODE (expected native or docker)" ;;
esac
# docker-compose.yml interpolates both of these, so they have to be in the environment.
export SPRING_PROFILES_ACTIVE BACKEND_PORT

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

# Gradle's toolchain pins Java 21. It can still find a JDK 21 elsewhere on the box
# even when a different one is first on PATH, so this warns instead of failing.
check_java_version() {
  local v
  v="$(java -version 2>&1 | grep -oE '"[0-9]+' | head -n1 | tr -d '"' || true)"
  [[ "$v" =~ ^[0-9]+$ ]] || return 0
  (( v >= 21 )) && return 0
  warn "java on PATH reports version $v, but the backend builds against Java 21.
       Point JAVA_HOME at a JDK 21 if Gradle cannot find one itself."
}

# ---------------------------------------------------------------- docker ---
# Only ever called in docker mode; native mode must not require the binary at all.
resolve_compose() {
  command -v docker >/dev/null 2>&1 || die "docker not found on PATH.
       Install Docker, or run the whole stack without it:  ./start.sh --mode native"
  # Tell "daemon is down" apart from "this user may not talk to it": the fixes differ,
  # and on a fresh Linux install the second one is what people actually hit.
  local docker_err
  if ! docker_err="$(docker info 2>&1 >/dev/null)"; then
    if [[ "$docker_err" == *"permission denied"* ]]; then
      die "Docker is installed but this user cannot reach its socket:
         ${docker_err%%$'\n'*}
       Add yourself to the docker group, then start a new login shell:
         sudo usermod -aG docker \"$USER\"
       Or leave Docker out of it and run the stack natively:
         ./start.sh --mode native"
    fi
    die "The Docker daemon is not reachable - start Docker Desktop, or run
       'sudo systemctl start docker'. You can also skip Docker entirely:
         ./start.sh --mode native"
  fi
  [[ -f "$BACKEND_COMPOSE" ]] || die "Missing $BACKEND_COMPOSE"

  local files=(-f "$BACKEND_COMPOSE")
  # qa resolves AWS credentials through the SDK's default chain, and inside a container
  # that chain cannot see the host's ~/.aws unless we mount it in.
  if [[ "$PROFILE" == "qa" && -d "$HOME/.aws" ]]; then
    [[ -f "$BACKEND_COMPOSE_AWS" ]] \
      || die "Missing $BACKEND_COMPOSE_AWS, which mounts ~/.aws into the qa container"
    files+=(-f "$BACKEND_COMPOSE_AWS")
  fi

  if docker compose version >/dev/null 2>&1; then
    COMPOSE=(docker compose "${files[@]}")
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE=(docker-compose "${files[@]}")
  else
    die "Neither 'docker compose' nor 'docker-compose' is installed.
       Add the Compose plugin, or run without Docker:  ./start.sh --mode native"
  fi
}

container_state() {
  docker inspect -f '{{.State.Status}}' "$BACKEND_CONTAINER" 2>/dev/null || printf 'missing'
}

# True while the backend is up, whichever mode started it.
backend_alive() {
  if [[ "$MODE" == "docker" ]]; then
    [[ "$(container_state)" == "running" ]]
  else
    [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null
  fi
}

# Docker keeps every profile's database inside one volume, and removing the volume is
# the only granularity Docker offers without a throwaway helper container.
clear_docker_database() {
  if ! docker volume inspect "$BACKEND_VOLUME" >/dev/null 2>&1; then
    info "No $BACKEND_VOLUME volume yet - nothing to clear."
    return 0
  fi
  warn "Removing volume $BACKEND_VOLUME: this deletes every profile's containerised
       database, not just $PROFILE's. Native-mode files in backend/ are untouched."
  if docker volume rm "$BACKEND_VOLUME" >/dev/null 2>&1; then
    ok "Cleared the container database volume; Flyway will recreate the schema on startup."
  else
    die "Could not remove volume $BACKEND_VOLUME - something is still attached to it.
       Find it with:  docker ps -a --filter volume=$BACKEND_VOLUME"
  fi
}

# ------------------------------------------------------------- utilities ---
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
       or a container publishing the port). A backend container left over from
       './start.sh --mode docker' is the usual culprit:
         docker compose -f backend/docker-compose.yml down
       Otherwise stop it by hand, or re-run with a different port:
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
  if [[ -n "$DOCKER_LOGS_PID" ]]; then kill "$DOCKER_LOGS_PID" 2>/dev/null || true; fi
  stop_pid "$FRONTEND_PID" "frontend"
  if $CONTAINER_STARTED; then
    info "Stopping the backend container..."
    "${COMPOSE[@]}" down >/dev/null 2>&1 \
      || warn "docker compose down failed; check 'docker ps' for $BACKEND_CONTAINER"
  else
    stop_pid "$BACKEND_PID" "backend"
  fi
  ok "All services stopped."
}
trap cleanup EXIT INT TERM

# --------------------------------------------------------------- preflight ---
mkdir -p "$LOG_DIR"

if $RUN_BACKEND; then
  [[ -f "$BACKEND_DIR/src/main/resources/application-${PROFILE}.yml" ]] \
    || die "No application-${PROFILE}.yml in $BACKEND_DIR/src/main/resources (known profiles: dev, qa)"

  if [[ "$MODE" == "docker" ]]; then
    resolve_compose
    # A container from an earlier run would hold the port and clash on the container
    # name, and it has to be gone before the database volume can be removed.
    "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1 || true
  else
    [[ -x "$BACKEND_DIR/gradlew" ]] || die "Missing $BACKEND_DIR/gradlew"
    command -v java >/dev/null 2>&1 || die "java not found on PATH (JDK 21 required).
       Install a JDK 21 (e.g. 'sudo apt install openjdk-21-jdk'), or run the backend
       in a container instead:  ./start.sh --mode docker"
    check_java_version
  fi

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
    if [[ "$MODE" == "docker" ]]; then
      info "Clearing the containerised database (volume $BACKEND_VOLUME)"
      clear_docker_database
    else
      info "Clearing the $PROFILE database at $DB_FILE"
      clear_database
    fi
  fi
fi

if $RUN_FRONTEND; then
  [[ -f "$FRONTEND_DIR/package.json" ]] || die "Missing $FRONTEND_DIR/package.json"
  command -v npm >/dev/null 2>&1 || die "npm not found on PATH (Node.js >= 18 required;
       the frontend runs on the host in both modes)"
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
  info "Starting backend on http://localhost:$BACKEND_PORT (profile: $PROFILE, mode: $MODE)"
  info "  log: $BACKEND_LOG"

  if [[ "$MODE" == "docker" ]]; then
    info "  database: volume $BACKEND_VOLUME$($CLEAR_DB && echo " (cleared)" || echo " (existing data kept)")"
    info "  building the image if it is missing or stale - the first build pulls the JDK"
    info "  image and downloads Gradle dependencies, so give it a few minutes"
    : >"$BACKEND_LOG"
    "${COMPOSE[@]}" up -d --build \
      || die "docker compose could not start the backend. Fix the error above, or run
       the backend straight from Gradle instead:  ./start.sh --mode native"
    CONTAINER_STARTED=true
    # Mirror container output into the file native mode writes to, so the tail at the
    # end of this script shows both services the same way in either mode.
    ( "${COMPOSE[@]}" logs -f --no-color --tail 200 ) >>"$BACKEND_LOG" 2>&1 &
    DOCKER_LOGS_PID=$!
  else
    info "  database: $DB_FILE$($CLEAR_DB && echo " (cleared)" || echo " (existing data kept)")"
    BACKEND_PID="$(start_bg "$BACKEND_DIR" "$BACKEND_LOG" \
      ./gradlew bootRun --console=plain "--args=--server.port=$BACKEND_PORT")"
  fi

  info "Waiting for backend to become healthy (up to ${HEALTH_TIMEOUT}s)..."
  deadline=$(( SECONDS + HEALTH_TIMEOUT ))
  until port_in_use "$BACKEND_PORT"; do
    if ! backend_alive; then
      warn "Backend stopped before it came up. Last 40 log lines:"
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
if $RUN_BACKEND;  then echo "  backend  http://localhost:$BACKEND_PORT ($MODE)"; fi
if $RUN_FRONTEND; then echo "  frontend http://localhost:$FRONTEND_PORT"; fi
printf '%s\n' "$C_RESET"

logs=()
if $RUN_BACKEND;  then logs+=("$LOG_DIR/backend.log"); fi
if $RUN_FRONTEND; then logs+=("$LOG_DIR/frontend.log"); fi
tail -n 0 -F "${logs[@]}" &
TAIL_PID=$!

# Exit as soon as either service dies, so we never leave a half-running stack.
while ! $SHUTTING_DOWN; do
  if $RUN_BACKEND && ! backend_alive; then
    warn "Backend exited."; break
  fi
  if [[ -n "$FRONTEND_PID" ]] && ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    warn "Frontend exited."; break
  fi
  sleep 2
done
