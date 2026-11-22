#!/bin/bash
# Starts Redis on 6379 in the background, with redis.conf from this folder (no Docker).
# Needs Redis installed once: brew install redis
# Logs: logs/redis.log   PID: run/redis.pid   Stop with ./stop-redis.sh
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v redis-server >/dev/null 2>&1; then
  echo "redis-server not found. Install it once: brew install redis"
  exit 1
fi
if lsof -nP -iTCP:6379 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "port 6379: already in use, skipped (Redis may already be running: redis-cli -p 6379 PING)"
  exit 0
fi

mkdir -p logs run
redis-server ./redis.conf

for _ in $(seq 1 25); do
  if redis-cli -p 6379 PING 2>/dev/null | grep -q PONG; then
    echo "port 6379: Redis $(redis-server --version | sed 's/.*v=\([^ ]*\).*/\1/') up (pid $(cat run/redis.pid))"
    exit 0
  fi
  sleep 0.2
done
echo "port 6379: NOT answering, see logs/redis.log"
exit 1
