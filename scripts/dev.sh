#!/usr/bin/env bash
#
# Developer mode: Keycloak in Docker, the three samples on your JDK, so you
# can change a library or a sample and see it at once. Needs JDK 21+.
# (Docker only, no JDK: `docker compose up --build` instead.)
#
#   ./scripts/dev.sh up       keycloak + build + start (the usual command)
#   ./scripts/dev.sh keycloak start Keycloak, import the realm, write env/
#   ./scripts/dev.sh build    ./mvnw install of every library and sample
#   ./scripts/dev.sh start    start the samples (after a build)
#   ./scripts/dev.sh stop     stop the samples
#   ./scripts/dev.sh down     stop the samples and Keycloak
#   ./scripts/dev.sh status
#   ./scripts/dev.sh logs <app>
#
# Logs and pid files: build/samples/.
set -euo pipefail

# Background JVMs and their pids are not reliable in Git Bash on Windows.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    echo "Developer mode needs Linux, macOS or WSL2 (Ubuntu) on Windows." >&2
    echo "From Git Bash or PowerShell, use the Docker mode: docker compose up --build -d" >&2
    exit 1
    ;;
esac
if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: 'docker compose' (Compose v2) is required. Install Docker Desktop, or Docker Engine with the docker-compose-plugin package." >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/build/samples"
SECRETS_FILE="$ROOT_DIR/env/trust-sample-client-secrets.env"
SAMPLES="trust-sample-downstream-api trust-sample-api trust-sample-bff"

port_of() {
  case "$1" in
    trust-sample-downstream-api) echo 9083 ;;
    trust-sample-api) echo 9082 ;;
    trust-sample-bff) echo 9081 ;;
  esac
}

pid_of() {
  local file="$RUN_DIR/$1.pid"
  [ -f "$file" ] && kill -0 "$(cat "$file")" 2>/dev/null && cat "$file"
}

keycloak() {
  if docker compose -f "$ROOT_DIR/docker-compose.yml" ps --status running --services 2>/dev/null | grep -q '^gateway$'; then
    echo "ERROR: the Docker samples are running (docker compose up). Stop them first: docker compose stop gateway trust-sample-bff trust-sample-api trust-sample-downstream-api" >&2
    exit 1
  fi
  docker compose -f "$ROOT_DIR/docker-compose.yml" up -d --wait keycloak
  docker compose -f "$ROOT_DIR/docker-compose.yml" run --rm keycloak-setup
}

build() {
  java -version >/dev/null 2>&1 || { echo "ERROR: no JDK on the PATH (JDK 21+ needed), or use: docker compose up --build" >&2; exit 1; }
  (cd "$ROOT_DIR" && ./mvnw -q -B install -DskipTests)
  echo "[OK] libraries and samples built"
}

start() {
  [ -f "$SECRETS_FILE" ] || { echo "ERROR: $SECRETS_FILE not found: run ./scripts/dev.sh keycloak first." >&2; exit 1; }
  mkdir -p "$RUN_DIR"
  for app in $SAMPLES; do
    if pid="$(pid_of "$app")"; then
      echo "[skip] $app already running (pid $pid)"
      continue
    fi
    local jar="$ROOT_DIR/$app/target/$app-0.0.1-SNAPSHOT.jar"
    [ -f "$jar" ] || { echo "ERROR: $jar not built: run ./scripts/dev.sh build first." >&2; exit 1; }
    # Only the java command is backgrounded, so $! is the JVM itself.
    (
      cd "$ROOT_DIR/$app" || exit 1
      TRUST_SAMPLE_SECRETS="$SECRETS_FILE" nohup java -jar "$jar" > "$RUN_DIR/$app.log" 2>&1 &
      echo $! > "$RUN_DIR/$app.pid"
    )
  done

  for app in $SAMPLES; do
    for _ in $(seq 1 60); do
      if grep -q "Started " "$RUN_DIR/$app.log" 2>/dev/null; then
        echo "[OK] $app  http://localhost:$(port_of "$app")"
        continue 2
      fi
      if ! pid_of "$app" >/dev/null; then
        echo "ERROR: $app stopped during startup ($RUN_DIR/$app.log):" >&2
        grep -A8 'APPLICATION FAILED' "$RUN_DIR/$app.log" >&2 || tail -20 "$RUN_DIR/$app.log" >&2
        exit 1
      fi
      sleep 1
    done
    echo "ERROR: $app did not start within 60s, see $RUN_DIR/$app.log" >&2
    exit 1
  done
  echo
  echo "Open http://localhost:9081 and log in as sample.user / sample."
}

stop() {
  for app in $SAMPLES; do
    if pid="$(pid_of "$app")"; then
      kill "$pid"
      # Wait for the JVM to exit, so the port is free for the next start.
      for _ in $(seq 1 30); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      kill -9 "$pid" 2>/dev/null || true
      echo "[OK] stopped $app"
    else
      echo "[skip] $app not running"
    fi
    rm -f "$RUN_DIR/$app.pid"
  done
}

status() {
  for app in $SAMPLES; do
    if pid="$(pid_of "$app")"; then
      echo "$app: running (pid $pid, http://localhost:$(port_of "$app"))"
    else
      echo "$app: stopped"
    fi
  done
  docker compose -f "$ROOT_DIR/docker-compose.yml" ps keycloak --format 'keycloak: {{.Status}}' 2>/dev/null || true
}

case "${1:-}" in
  up) keycloak; build; start ;;
  keycloak) keycloak ;;
  build) build ;;
  start) start ;;
  stop) stop ;;
  down) stop; docker compose -f "$ROOT_DIR/docker-compose.yml" stop keycloak ;;
  status) status ;;
  logs) tail -f "$RUN_DIR/${2:?usage: logs <app>}.log" ;;
  *) sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 2 ;;
esac
