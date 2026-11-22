#!/bin/bash
# Stops the Redis that ./start-redis.sh started. Nothing is saved (the data is only a cache).
set -uo pipefail
cd "$(dirname "$0")"

if redis-cli -p 6379 SHUTDOWN NOSAVE >/dev/null 2>&1; then
  echo "port 6379: Redis stopped"
else
  echo "port 6379: Redis was not running"
fi
rm -f run/redis.pid
