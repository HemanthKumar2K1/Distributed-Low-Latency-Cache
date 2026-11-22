#!/bin/bash
# Starts the Cache System nodes as plain background JVMs (no Docker).
#   ./start-nodes.sh                 nodes on 7001 7002 7003
#   ./start-nodes.sh 7001 7002       only these ports
# Logs: logs/node-<port>.log   PIDs: run/node-<port>.pid   Stop with ./stop-nodes.sh
# A port that is already in use is skipped (a node may already be running there).
# Exits 1 if any port is not answering PING at the end.
set -euo pipefail
cd "$(dirname "$0")"

JAR=target/cache-system.jar
# Small heap on purpose: 3 cache nodes, Product Catalogue, the load tester and PostgreSQL share one laptop.
HEAP=256m
# Wait for each node to answer PING: up to 25 tries x 0.2 s = about 5 s.
TRIES=25
PAUSE_SECONDS=0.2

PORTS=("$@")
if [ ${#PORTS[@]} -eq 0 ]; then
  PORTS=(7001 7002 7003)
fi

if [ ! -f "$JAR" ]; then
  echo "No $JAR yet. Build it first: ./mvnw -q package -DskipTests"
  exit 1
fi
mkdir -p logs run

for port in "${PORTS[@]}"; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "port $port: already in use, skipped"
    continue
  fi
  # --allow-shutdown: lets Control Room's "Stop node" button stop this node (failure test). A plain java -jar node refuses.
  nohup java -Xmx"$HEAP" -jar "$JAR" --port="$port" --allow-shutdown=true > "logs/node-$port.log" 2>&1 &
  echo $! > "run/node-$port.pid"
  echo "port $port: started (pid $!)"
done

# Check every port answers, including ports that were skipped because a node was already there.
# Exit code 1 if any port does not answer, so a calling script can stop instead of testing against a missing node.
FAILED=0
for port in "${PORTS[@]}"; do
  for _ in $(seq 1 "$TRIES"); do
    if printf 'PING\r\nQUIT\r\n' | nc -w 1 127.0.0.1 "$port" 2>/dev/null | grep -q PONG; then
      echo "port $port: up"
      continue 2   # go to the next port
    fi
    sleep "$PAUSE_SECONDS"
  done
  echo "port $port: NOT answering, see logs/node-$port.log"
  FAILED=1
done
exit "$FAILED"
