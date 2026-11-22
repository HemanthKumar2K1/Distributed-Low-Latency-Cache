#!/bin/bash
# Stops the nodes that ./start-nodes.sh started (only those: it reads run/node-<port>.pid).
#   ./stop-nodes.sh                  all of them
#   ./stop-nodes.sh 7002             just this one
# A node you started yourself with java -jar is not touched: stop it with Ctrl+C.
# No -e here: if one kill fails, keep going and stop the others.
set -uo pipefail
cd "$(dirname "$0")"
# With no pid files, run/node-*.pid matches nothing instead of staying as literal text.
shopt -s nullglob

if [ $# -gt 0 ]; then
  FILES=()
  for port in "$@"; do
    FILES+=("run/node-$port.pid")
  done
else
  FILES=(run/node-*.pid)
fi

if [ ${#FILES[@]} -eq 0 ]; then
  echo "no nodes started by start-nodes.sh are running"
  exit 0
fi

for f in "${FILES[@]}"; do
  if [ ! -f "$f" ]; then
    echo "$f: no such file, nothing to stop"
    continue
  fi
  pid=$(cat "$f")
  # run/node-7002.pid -> 7002
  port=${f#run/node-}
  port=${port%.pid}
  # The pid file may be stale: the node died and the OS gave its pid to some other program.
  # Only kill the pid if it is still our node, i.e. cache-system.jar on this port.
  command=$(ps -p "$pid" -o command= 2>/dev/null || true)
  if [[ "$command" != *cache-system.jar*"--port=$port"* ]]; then
    echo "port $port: pid $pid is not this node any more, nothing stopped"
    rm -f "$f"
    continue
  fi
  if kill "$pid" 2>/dev/null; then
    echo "port $port: stopped (pid $pid)"
  else
    echo "port $port: pid $pid was not running"
  fi
  rm -f "$f"
done
