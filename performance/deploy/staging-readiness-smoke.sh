#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
GRAFANA_USER="${GRAFANA_USER:-admin}"
GRAFANA_PASSWORD="${GRAFANA_PASSWORD:-admin}"
JOB_SEARCH_KEYWORD="${JOB_SEARCH_KEYWORD:-backend}"

echo "BASE_URL=${BASE_URL}"
echo "BACKEND_URL=${BACKEND_URL}"
echo "GATEWAY_URL=${GATEWAY_URL}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "GRAFANA_URL=${GRAFANA_URL}"
echo "ZIPKIN_URL=${ZIPKIN_URL}"
echo "ELASTICSEARCH_URL=${ELASTICSEARCH_URL}"
echo "JOB_SEARCH_KEYWORD=${JOB_SEARCH_KEYWORD}"
echo

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
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
require_command jq

echo "### Health endpoints"
backend_health="$(
  curl --fail --silent --show-error \
    "${BACKEND_URL}/actuator/health"
)"
gateway_health="$(
  curl --fail --silent --show-error \
    "${GATEWAY_URL}/actuator/health"
)"

assert_contains "${backend_health}" '"status":"UP"' "backend health should be UP"
assert_contains "${gateway_health}" '"status":"UP"' "gateway health should be UP"

echo "backend_health=UP"
echo "gateway_health=UP"
echo

echo "### Gateway API routing"
auth_me_status="$(
  curl --silent --show-error \
    --output /tmp/jobflow-auth-me-response.json \
    --write-out "%{http_code}" \
    "${BASE_URL}/auth/me"
)"
assert_status "${auth_me_status}" "401" "GET /auth/me without token should return 401"

jobs_status="$(
  curl --fail --silent --show-error \
    --get \
    --data-urlencode "keyword=${JOB_SEARCH_KEYWORD}" \
    --data-urlencode "limit=1" \
    --output /tmp/jobflow-jobs-search-response.json \
    --write-out "%{http_code}" \
    "${BASE_URL}/jobs/search"
)"
assert_status "${jobs_status}" "200" "GET /jobs/search should return 200"

jobs_success="$(jq -r '.success' /tmp/jobflow-jobs-search-response.json)"
assert_status "${jobs_success}" "true" "GET /jobs/search success should be true"

echo "auth_me_without_token_status=${auth_me_status}"
echo "jobs_search_status=${jobs_status}"
echo "jobs_search_success=${jobs_success}"
echo

echo "### OpenAPI"
openapi_json="$(
  curl --fail --silent --show-error \
    "${BASE_URL}/v3/api-docs"
)"
openapi_title="$(jq -r '.info.title' <<< "${openapi_json}")"
openapi_version="$(jq -r '.openapi' <<< "${openapi_json}")"
openapi_path_count="$(jq -r '.paths | length' <<< "${openapi_json}")"
has_bearer_auth="$(jq -r '.components.securitySchemes | has("bearerAuth")' <<< "${openapi_json}")"

assert_status "${openapi_title}" "JobFlow API" "OpenAPI title should be JobFlow API"
assert_status "${has_bearer_auth}" "true" "OpenAPI should expose bearerAuth"

curl --fail --silent --show-error \
  --output /dev/null \
  "${BASE_URL}/swagger-ui/index.html"

echo "openapi_title=${openapi_title}"
echo "openapi_version=${openapi_version}"
echo "openapi_path_count=${openapi_path_count}"
echo "has_bearer_auth=${has_bearer_auth}"
echo "swagger_ui_status=200"
echo

echo "### Elasticsearch"
es_health="$(
  curl --fail --silent --show-error \
    "${ELASTICSEARCH_URL}/_cluster/health"
)"
es_status="$(jq -r '.status' <<< "${es_health}")"
es_index_count="$(
  curl --fail --silent --show-error \
    "${ELASTICSEARCH_URL}/_cat/indices?format=json" \
    | jq '[.[] | select(.index | contains("job"))] | length'
)"

if [[ "${es_status}" != "green" && "${es_status}" != "yellow" ]]; then
  echo "Assertion failed: Elasticsearch cluster should be green or yellow" >&2
  echo "Actual status: ${es_status}" >&2
  echo "${es_health}" >&2
  exit 1
fi

echo "elasticsearch_status=${es_status}"
echo "job_related_index_count=${es_index_count}"
echo

echo "### Prometheus"
prometheus_ready="$(
  curl --fail --silent --show-error \
    "${PROMETHEUS_URL}/-/ready"
)"
assert_contains "${prometheus_ready}" "Prometheus Server is Ready" "Prometheus should be ready"

targets_response="$(
  curl --fail --silent --show-error \
    "${PROMETHEUS_URL}/api/v1/targets"
)"

backend_target_health="$(
  jq -r '
    [.data.activeTargets[]
      | select(.labels.job == "jobflow-backend")
      | .health
    ][0] // "missing"
  ' <<< "${targets_response}"
)"
gateway_target_health="$(
  jq -r '
    [.data.activeTargets[]
      | select(.labels.job == "jobflow-gateway")
      | .health
    ][0] // "missing"
  ' <<< "${targets_response}"
)"

assert_status "${backend_target_health}" "up" "Prometheus backend target should be up"
assert_status "${gateway_target_health}" "up" "Prometheus gateway target should be up"

echo "prometheus_ready=true"
echo "prometheus_backend_target=${backend_target_health}"
echo "prometheus_gateway_target=${gateway_target_health}"
echo

echo "### Grafana"
grafana_health="$(
  curl --fail --silent --show-error \
    "${GRAFANA_URL}/api/health"
)"
grafana_database_status="$(jq -r '.database' <<< "${grafana_health}")"
assert_status "${grafana_database_status}" "ok" "Grafana database should be ok"

datasources_response="$(
  curl --fail --silent --show-error \
    --user "${GRAFANA_USER}:${GRAFANA_PASSWORD}" \
    "${GRAFANA_URL}/api/datasources"
)"
has_prometheus_datasource="$(
  jq -r '[.[] | select(.uid == "Prometheus")] | length > 0' <<< "${datasources_response}"
)"
assert_status "${has_prometheus_datasource}" "true" "Grafana should have Prometheus datasource"

echo "grafana_database=${grafana_database_status}"
echo "grafana_prometheus_datasource=${has_prometheus_datasource}"
echo

echo "### Zipkin"
zipkin_services="$(
  curl --fail --silent --show-error \
    "${ZIPKIN_URL}/api/v2/services"
)"
zipkin_service_count="$(jq -r 'length' <<< "${zipkin_services}")"

echo "zipkin_status=200"
echo "zipkin_service_count=${zipkin_service_count}"
echo

echo "### Staging Readiness Smoke Summary"
echo "backend_health=UP"
echo "gateway_health=UP"
echo "auth_me_without_token_status=${auth_me_status}"
echo "jobs_search_status=${jobs_status}"
echo "openapi_path_count=${openapi_path_count}"
echo "elasticsearch_status=${es_status}"
echo "job_related_index_count=${es_index_count}"
echo "prometheus_backend_target=${backend_target_health}"
echo "prometheus_gateway_target=${gateway_target_health}"
echo "grafana_database=${grafana_database_status}"
echo "grafana_prometheus_datasource=${has_prometheus_datasource}"
echo "zipkin_service_count=${zipkin_service_count}"

echo
echo "Staging readiness smoke completed."
