#!/usr/bin/env bash

set -euo pipefail

MIN_MEMORY_MB="${MIN_MEMORY_MB:-3500}"
MIN_DISK_AVAILABLE_MB="${MIN_DISK_AVAILABLE_MB:-20000}"
if [[ -z "${REQUIRED_PORTS+x}" ]]; then
  REQUIRED_PORTS="8080 8081 3001 9090 9200 9411"
fi

echo "MIN_MEMORY_MB=${MIN_MEMORY_MB}"
echo "MIN_DISK_AVAILABLE_MB=${MIN_DISK_AVAILABLE_MB}"
echo "REQUIRED_PORTS=${REQUIRED_PORTS}"
echo

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    fail "required command '${command_name}' is not installed"
  fi

  echo "${command_name}=ok"
}

print_version() {
  local label="$1"
  shift

  echo "${label}=$("$@" 2>&1 | head -n 1)"
}

memory_mb() {
  if command -v free >/dev/null 2>&1; then
    free -m | awk '/^Mem:/ {print $2}'
    return
  fi

  if [[ "$(uname -s)" == "Darwin" ]]; then
    sysctl -n hw.memsize | awk '{printf "%.0f\n", $1 / 1024 / 1024}'
    return
  fi

  fail "cannot determine system memory"
}

disk_available_mb() {
  df -Pm . | awk 'NR == 2 {print $4}'
}

port_in_use() {
  local port="$1"

  if command -v lsof >/dev/null 2>&1; then
    lsof -iTCP:"${port}" -sTCP:LISTEN -Pn >/dev/null 2>&1
    return
  fi

  if command -v ss >/dev/null 2>&1; then
    ss -ltn | awk '{print $4}' | grep -Eq "[:.]${port}$"
    return
  fi

  if command -v netstat >/dev/null 2>&1; then
    netstat -ltn 2>/dev/null | awk '{print $4}' | grep -Eq "[:.]${port}$"
    return
  fi

  fail "cannot check port ${port}; install lsof, ss, or netstat"
}

echo "### Required commands"
require_command docker
require_command curl
require_command jq
require_command git
echo

echo "### Versions"
print_version docker_version docker --version
print_version docker_compose_version docker compose version
print_version curl_version curl --version
print_version jq_version jq --version
print_version git_version git --version
echo

echo "### Docker daemon"
docker info >/dev/null 2>&1 || fail "Docker daemon is not running or current user cannot access it"
echo "docker_daemon=ok"
echo

echo "### System resources"
total_memory_mb="$(memory_mb)"
available_disk_mb="$(disk_available_mb)"
echo "total_memory_mb=${total_memory_mb}"
echo "available_disk_mb=${available_disk_mb}"

if (( total_memory_mb < MIN_MEMORY_MB )); then
  fail "memory is too small. expected >= ${MIN_MEMORY_MB}MB, actual=${total_memory_mb}MB"
fi

if (( available_disk_mb < MIN_DISK_AVAILABLE_MB )); then
  fail "available disk is too small. expected >= ${MIN_DISK_AVAILABLE_MB}MB, actual=${available_disk_mb}MB"
fi

echo "system_resources=ok"
echo

echo "### Required ports"
if [[ -z "${REQUIRED_PORTS}" ]]; then
  echo "required_ports=skipped"
else
  for port in ${REQUIRED_PORTS}; do
    if port_in_use "${port}"; then
      fail "port ${port} is already in use"
    fi

    echo "port_${port}=available"
  done
fi
echo

echo "### Docker Compose config"
docker compose -f docker-compose.yml -f docker-compose.performance.yml config >/dev/null
echo "docker_compose_config=ok"
echo

echo "### Server Bootstrap Check Summary"
echo "required_commands=ok"
echo "docker_daemon=ok"
echo "system_resources=ok"
if [[ -z "${REQUIRED_PORTS}" ]]; then
  echo "required_ports=skipped"
else
  echo "required_ports=ok"
fi
echo "docker_compose_config=ok"
echo
echo "Server bootstrap check completed."
