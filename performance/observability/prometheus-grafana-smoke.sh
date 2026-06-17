#!/usr/bin/env bash

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://127.0.0.1:3001}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
MONTH="${MONTH:-2026-06-01}"
LIMIT="${LIMIT:-10}"

echo "BACKEND_URL=${BACKEND_URL}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "GRAFANA_URL=${GRAFANA_URL}"
echo "MONTH=${MONTH}"
echo "LIMIT=${LIMIT}"
echo

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

require_command curl

query_prometheus() {
  local query="$1"

  curl --fail --silent --show-error \
    --get \
    --data-urlencode "query=${query}" \
    "${PROMETHEUS_URL}/api/v1/query"
}

count_prometheus_results() {
  local query="$1"

  query_prometheus "${query}" \
    | grep -o '"result":\[' \
    | wc -l \
    | tr -d ' '
}

assert_contains() {
  local value="$1"
  local expected="$2"
  local message="$3"

  if [[ "${value}" != *"${expected}"* ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected to contain: ${expected}" >&2
    echo "Actual:" >&2
    echo "${value}" >&2
    exit 1
  fi
}

assert_prometheus_has_result() {
  local query="$1"
  local label="$2"
  local response

  response="$(query_prometheus "${query}")"
  assert_contains "${response}" '"status":"success"' "${label} query should succeed"

  if [[ "${response}" == *'"result":[]'* ]]; then
    echo "Assertion failed: ${label} query returned empty result" >&2
    echo "Query: ${query}" >&2
    echo "Response:" >&2
    echo "${response}" >&2
    exit 1
  fi

  echo "${response}"
}

echo "### Warm backend metrics"
curl --fail --silent --show-error "${BACKEND_URL}/actuator/health" >/dev/null
curl --fail --silent --show-error "${BACKEND_URL}/actuator/prometheus" >/dev/null

# Cache metric에 hit/miss 값이 잡히도록 cache 대상 API를 두 번 호출한다.
curl --fail --silent --show-error \
  "${BACKEND_URL}/trends/skills?periodStart=${MONTH}&limit=${LIMIT}" >/dev/null
curl --fail --silent --show-error \
  "${BACKEND_URL}/trends/skills?periodStart=${MONTH}&limit=${LIMIT}" >/dev/null

echo "Backend warm-up completed."
echo

echo "### Prometheus readiness"
prometheus_ready="$(curl --fail --silent --show-error "${PROMETHEUS_URL}/-/ready")"
echo "${prometheus_ready}"
assert_contains "${prometheus_ready}" "Prometheus Server is Ready" "Prometheus should be ready"
echo

echo "### Prometheus targets"
targets_response="$(curl --fail --silent --show-error "${PROMETHEUS_URL}/api/v1/targets")"
assert_contains "${targets_response}" '"job":"jobflow-backend"' "Prometheus target should include jobflow-backend"
assert_contains "${targets_response}" '"health":"up"' "Prometheus target should be up"
echo "${targets_response}" \
  | tr ',' '\n' \
  | grep -E '"job"|"health"|"scrapeUrl"|"lastError"' || true
echo

echo "### Prometheus metric queries"
http_metric_response="$(assert_prometheus_has_result \
  'http_server_requests_seconds_count{application="jobflow-backend"}' \
  'http_server_requests_seconds_count')"
cache_metric_response="$(assert_prometheus_has_result \
  'cache_gets_total{application="jobflow-backend"}' \
  'cache_gets_total')"
jvm_metric_response="$(assert_prometheus_has_result \
  'jvm_memory_used_bytes{application="jobflow-backend"}' \
  'jvm_memory_used_bytes')"

http_metric_count="$(echo "${http_metric_response}" | grep -o '"metric":{' | wc -l | tr -d ' ')"
cache_metric_count="$(echo "${cache_metric_response}" | grep -o '"metric":{' | wc -l | tr -d ' ')"
jvm_metric_count="$(echo "${jvm_metric_response}" | grep -o '"metric":{' | wc -l | tr -d ' ')"

echo "http_metric_count=${http_metric_count}"
echo "cache_metric_count=${cache_metric_count}"
echo "jvm_metric_count=${jvm_metric_count}"
echo

echo "### Grafana health"
grafana_health="$(curl --fail --silent --show-error "${GRAFANA_URL}/api/health")"
echo "${grafana_health}"
if ! echo "${grafana_health}" | grep -Eq '"database"[[:space:]]*:[[:space:]]*"ok"'; then
  echo "Assertion failed: Grafana database should be ok" >&2
  echo "Actual:" >&2
  echo "${grafana_health}" >&2
  exit 1
fi
echo

echo "### Grafana datasource provisioning"
datasources_response="$(curl --fail --silent --show-error \
  --user "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
  "${GRAFANA_URL}/api/datasources")"
assert_contains "${datasources_response}" '"uid":"Prometheus"' "Grafana datasource uid should be Prometheus"
assert_contains "${datasources_response}" '"url":"http://prometheus:9090"' "Grafana datasource should point to prometheus service"
echo "${datasources_response}" \
  | tr '{' '\n' \
  | grep -E '"uid":"Prometheus"|"name":"Prometheus"|"url":"http://prometheus:9090"' || true
echo

echo "### Grafana dashboard provisioning"
dashboards_response="$(curl --fail --silent --show-error \
  --user "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
  "${GRAFANA_URL}/api/search?query=JobFlow")"
assert_contains "${dashboards_response}" '"title":"JobFlow Backend Observability"' \
  "Grafana dashboard should include JobFlow Backend Observability"
assert_contains "${dashboards_response}" '"uid":"jobflow-backend"' \
  "Grafana dashboard uid should be jobflow-backend"
echo "${dashboards_response}" \
  | tr '{' '\n' \
  | grep -E '"title":"JobFlow"|"title":"JobFlow Backend Observability"|"uid":"jobflow-backend"' || true
echo

echo "### Observability Smoke Summary"
echo "prometheus_ready=true"
echo "prometheus_target_up=true"
echo "http_metric_count=${http_metric_count}"
echo "cache_metric_count=${cache_metric_count}"
echo "jvm_metric_count=${jvm_metric_count}"
echo "grafana_health_ok=true"
echo "grafana_datasource_uid=Prometheus"
echo "grafana_dashboard_uid=jobflow-backend"

echo
echo "Observability stack smoke completed."
