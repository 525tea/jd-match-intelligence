#!/usr/bin/env bash
set -euo pipefail

ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance/host-tuning}"
SNAPSHOT_FILE="${SNAPSHOT_FILE:-$(date +%y%m%d_%H%M%S)_host_tuning_snapshot.txt}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cd "$ROOT_DIR"
mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"
OUTPUT_PATH="${ARTIFACT_DIR}/${SNAPSHOT_FILE}"

{
  echo "timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo "hostname=$(hostname)"
  echo "kernel=$(uname -a)"
  echo

  echo "### CPU"
  if command -v lscpu >/dev/null 2>&1; then
    lscpu
  elif command -v nproc >/dev/null 2>&1; then
    echo "cpu_count=$(nproc)"
  else
    sysctl -n hw.ncpu 2>/dev/null | awk '{print "cpu_count="$1}' || true
  fi
  echo

  echo "### Memory"
  if command -v free >/dev/null 2>&1; then
    free -h
  else
    sysctl -n hw.memsize 2>/dev/null | awk '{printf "mem_total_bytes=%s\n", $1}' || true
  fi
  echo

  echo "### Process limits"
  echo "ulimit_n=$(ulimit -n)"
  echo "ulimit_u=$(ulimit -u)"
  echo

  echo "### sysctl"
  for key in \
    net.core.somaxconn \
    net.ipv4.tcp_max_syn_backlog \
    net.core.netdev_max_backlog \
    net.ipv4.ip_local_port_range \
    fs.file-max; do
    sysctl "$key" 2>/dev/null || true
  done
  echo

  echo "### Docker compose services"
  docker compose -f docker-compose.yml -f docker-compose.performance.yml ps 2>/dev/null || true
  echo

  echo "### Docker stats"
  docker stats --no-stream 2>/dev/null || true
  echo

  echo "### Backend tuning env"
  docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T backend sh -lc \
    'env | grep -E "^(SERVER_TOMCAT|HIKARI|JAVA_TOOL_OPTIONS|CACHE_ENABLED|ELASTICSEARCH_REINDEX_ON_STARTUP|SPRING_PROFILES_ACTIVE)=" | sort' \
    2>/dev/null || true
} | tee "$OUTPUT_PATH"

echo
echo "host_tuning_snapshot=$OUTPUT_PATH"
