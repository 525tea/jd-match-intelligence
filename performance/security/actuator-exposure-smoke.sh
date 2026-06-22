#!/usr/bin/env bash

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
BASE_URL="${BASE_URL:-http://localhost:8081/api}"

echo "BACKEND_URL=${BACKEND_URL}"
echo "GATEWAY_URL=${GATEWAY_URL}"
echo "BASE_URL=${BASE_URL}"
echo

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

assert_status() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if [[ "${actual}" != "${expected}" ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected: ${expected}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

require_command curl

echo "### Direct actuator endpoints"
backend_health_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-backend-health.json \
    --write-out "%{http_code}" \
    "${BACKEND_URL}/actuator/health"
)"
backend_prometheus_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-backend-prometheus.txt \
    --write-out "%{http_code}" \
    "${BACKEND_URL}/actuator/prometheus"
)"
gateway_health_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-gateway-health.json \
    --write-out "%{http_code}" \
    "${GATEWAY_URL}/actuator/health"
)"
gateway_prometheus_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-gateway-prometheus.txt \
    --write-out "%{http_code}" \
    "${GATEWAY_URL}/actuator/prometheus"
)"

assert_status "${backend_health_status}" "200" "backend direct actuator health should be available for internal smoke"
assert_status "${backend_prometheus_status}" "200" "backend direct actuator prometheus should be available for Prometheus scrape"
assert_status "${gateway_health_status}" "200" "gateway direct actuator health should be available"
assert_status "${gateway_prometheus_status}" "200" "gateway direct actuator prometheus should be available for Prometheus scrape"

echo "backend_health_status=${backend_health_status}"
echo "backend_prometheus_status=${backend_prometheus_status}"
echo "gateway_health_status=${gateway_health_status}"
echo "gateway_prometheus_status=${gateway_prometheus_status}"
echo

echo "### Gateway-proxied backend actuator boundary"
proxied_backend_health_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-proxied-backend-health.json \
    --write-out "%{http_code}" \
    "${BASE_URL}/actuator/health"
)"
proxied_backend_prometheus_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-proxied-backend-prometheus.txt \
    --write-out "%{http_code}" \
    "${BASE_URL}/actuator/prometheus"
)"

assert_status "${proxied_backend_health_status}" "404" "gateway should not expose backend actuator health through /api"
assert_status "${proxied_backend_prometheus_status}" "404" "gateway should not expose backend actuator prometheus through /api"

echo "proxied_backend_health_status=${proxied_backend_health_status}"
echo "proxied_backend_prometheus_status=${proxied_backend_prometheus_status}"
echo

echo "### Actuator Exposure Smoke Summary"
echo "backend_health_status=${backend_health_status}"
echo "backend_prometheus_status=${backend_prometheus_status}"
echo "gateway_health_status=${gateway_health_status}"
echo "gateway_prometheus_status=${gateway_prometheus_status}"
echo "proxied_backend_health_status=${proxied_backend_health_status}"
echo "proxied_backend_prometheus_status=${proxied_backend_prometheus_status}"

echo
echo "Actuator exposure smoke completed."
