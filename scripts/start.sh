#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$ROOT_DIR/.runtime/tradepass.pid"
LOG_FILE="$ROOT_DIR/.runtime/tradepass.log"
JAR="$ROOT_DIR/backend/target/tradepass-server-0.1.0-SNAPSHOT.jar"
SPRING_PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"

RED='\033[0;31m'; GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }
log_err()  { echo -e "${RED}[ERROR]${NC} $*"; }

# ---------- infra ----------
start_infra() {
  log_info "启动 Docker 基础设施 (MySQL)..."
  if ! docker info >/dev/null 2>&1; then
    log_err "Docker 未运行"
    return 1
  fi
  docker compose -f "$ROOT_DIR/docker-compose.yml" up -d --wait 2>/dev/null || true
  log_info "基础设施已就绪"
}

# ---------- app ----------
start_app() {
  mkdir -p "$ROOT_DIR/.runtime"

  if [[ -f "$PID_FILE" ]]; then
    local pid=$(cat "$PID_FILE")
    if kill -0 "$pid" 2>/dev/null; then
      log_info "tradepass 已在运行 (pid=$pid)"
      return 0
    fi
    rm -f "$PID_FILE"
  fi

  if [[ ! -f "$JAR" ]] || [[ -n "$(find "$ROOT_DIR/backend/src" -type f -newer "$JAR" -print -quit)" ]]; then
    log_info "后端源码有更新，执行 mvn package..."
    cd "$ROOT_DIR/backend" && mvn -q -DskipTests package && cd "$ROOT_DIR"
  fi

  log_info "启动 tradepass ($SPRING_PROFILE) → $JAR"
  nohup java -Xms256m -Xmx512m -jar "$JAR" --spring.profiles.active="$SPRING_PROFILE" > "$LOG_FILE" 2>&1 &
  echo "$!" > "$PID_FILE"

  local i=0
  while (( i < 30 )); do
    if curl -s http://localhost:9999/api/me >/dev/null 2>&1; then
      log_info "tradepass (:9999) 就绪"
      return 0
    fi
    sleep 2
    ((i++))
  done
  log_err "tradepass 30s 内未就绪，检查 $LOG_FILE"
  return 1
}

# ---------- main ----------
main() {
  start_infra || exit 1
  start_app || exit 1
  echo "=============================="
  echo "  API: http://localhost:9999/api"
  echo "=============================="
}
main
