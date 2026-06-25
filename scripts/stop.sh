#!/usr/bin/env bash
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_FILE="$ROOT_DIR/.runtime/tradepass.pid"

GREEN='\033[0;32m'; NC='\033[0m'
log_info() { echo -e "${GREEN}[INFO]${NC} $*"; }

# 停应用
if [[ -f "$PID_FILE" ]]; then
  pid=$(cat "$PID_FILE")
  if kill -0 "$pid" 2>/dev/null; then
    log_info "停止 tradepass (pid=$pid)..."
    kill "$pid" 2>/dev/null
    sleep 3
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
  fi
  rm -f "$PID_FILE"
fi

# 停 Docker
if docker info >/dev/null 2>&1; then
  log_info "停止 Docker 基础设施..."
  docker compose -f "$ROOT_DIR/docker-compose.yml" down 2>/dev/null
fi

log_info "全部已关闭"
