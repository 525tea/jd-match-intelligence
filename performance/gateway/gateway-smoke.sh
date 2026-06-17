#!/usr/bin/env bash

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:8081}"
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://127.0.0.1:9090}"
KEYWORD="${KEYWORD:-백엔드}"
LIMIT="${LIMIT:-5}"
RATE_LIMIT_REQUESTS="${RATE_LIMIT_REQUESTS:-120}"
EXPECT_RATE_LIMIT="${EXPECT_RATE_LIMIT:-true}"
EXPECT_FALLBACK="${EXPECT_FALLBACK:-true}"

echo "GATEWAY_URL=${GATEWAY_URL}"
echo "BACKEND_SERVICE=${BACKEND_SERVICE}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "KEYWORD=${KEYWORD}"
echo "LIMIT=${LIMIT}"
echo "RATE_LIMIT_REQUESTS=${RATE_LIMIT_REQUESTS}"
echo "EXPECT_RATE_LIMIT=${EXPECT_RATE_LIMIT}"
echo "EXPECT_FALLBACK=${EXPECT_FALLBACK}"
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

require_command curl
require_command docker

echo "### Gateway health"
gateway_health="$(curl --fail --silent --show-error "${GATEWAY_URL}/actuator/health")"
echo "${gateway_health}"
assert_contains "${gateway_health}" '"status":"UP"' "Gateway health should be UP"
echo

echo "### Gateway routing"
routing_response="$(curl --fail --silent --show-error \
  --get "${GATEWAY_URL}/api/jobs/search" \
  --data-urlencode "keyword=${KEYWORD}" \
  --data-urlencode "limit=${LIMIT}")"
echo "${routing_response}"
assert_contains "${routing_response}" '"success":true' "Gateway should route public job search API"
assert_contains "${routing_response}" '"data":[' "Gateway routing response should include data array"
echo

echo "### Gateway fixed-window rate limit"
rate_limit_429_count=0
last_rate_limit_status=""

for i in $(seq 1 "${RATE_LIMIT_REQUESTS}"); do
  http_code="$(curl --silent --output /dev/null --write-out "%{http_code}" \
    "${GATEWAY_URL}/api/jobs/search?keyword=backend&limit=1")"
  last_rate_limit_status="${http_code}"

  if [[ "${http_code}" == "429" ]]; then
    rate_limit_429_count=$((rate_limit_429_count + 1))
  fi

  echo "${i} ${http_code}"
done

if [[ "${EXPECT_RATE_LIMIT}" == "true" && "${rate_limit_429_count}" -eq 0 ]]; then
  echo "Assertion failed: Gateway rate limit should return at least one 429 response" >&2
  exit 1
fi

echo "rate_limit_429_count=${rate_limit_429_count}"
echo "last_rate_limit_status=${last_rate_limit_status}"
echo

echo "### Gateway circuit breaker fallback"
fallback_status="skipped"
fallback_body=""

if [[ "${EXPECT_FALLBACK}" == "true" ]]; then
  docker compose stop "${BACKEND_SERVICE}" >/dev/null

  set +e
  fallback_response="$(
    curl --silent --show-error \
      --write-out $'\n%{http_code}' \
      --get "${GATEWAY_URL}/api/jobs/search" \
      --data-urlencode "keyword=${KEYWORD}" \
      --data-urlencode "limit=${LIMIT}"
  )"
  curl_exit=$?
  set -e

  docker compose start "${BACKEND_SERVICE}" >/dev/null

  if [[ "${curl_exit}" -ne 0 ]]; then
    echo "Assertion failed: Gateway fallback request failed before receiving an HTTP response" >&2
    echo "${fallback_response}" >&2
    exit 1
  fi

  fallback_status="$(tail -n 1 <<< "${fallback_response}")"
  fallback_body="$(sed '$d' <<< "${fallback_response}")"

  echo "${fallback_body}"
  echo "fallback_status=${fallback_status}"

  if [[ "${fallback_status}" != "503" ]]; then
    echo "Assertion failed: Gateway fallback should return 503" >&2
    exit 1
  fi

  assert_contains "${fallback_body}" '"success":false' "Fallback response should fail explicitly"
  assert_contains "${fallback_body}" '"code":"GATEWAY_BACKEND_UNAVAILABLE"' "Fallback response should include gateway error code"
else
  echo "Fallback check skipped."
fi
echo

echo "### Backend recovery routing"
# backend start 직후 Tomcat이 열리기 전일 수 있어 짧게 재시도한다.
recovered=false
for i in {1..20}; do
  if recovery_response="$(curl --fail --silent --show-error \
    --get "${GATEWAY_URL}/api/jobs/search" \
    --data-urlencode "keyword=${KEYWORD}" \
    --data-urlencode "limit=${LIMIT}")"; then
    recovered=true
    break
  fi
  sleep 1
done

if [[ "${recovered}" != "true" ]]; then
  echo "Assertion failed: backend did not recover behind gateway" >&2
  exit 1
fi

echo "${recovery_response}"
assert_contains "${recovery_response}" '"success":true' "Gateway should route again after backend recovery"
echo

echo "### Gateway Prometheus metrics"
if curl --fail --silent --show-error "${PROMETHEUS_URL}/-/ready" >/dev/null 2>&1; then
  prometheus_response="$(curl --fail --silent --show-error \
    --get "${PROMETHEUS_URL}/api/v1/query" \
    --data-urlencode 'query=http_server_requests_seconds_count{application="jobflow-gateway"}')"

  echo "${prometheus_response}"
  assert_contains "${prometheus_response}" '"status":"success"' "Prometheus gateway metric query should succeed"

  if [[ "${prometheus_response}" == *'"result":[]'* ]]; then
    echo "Assertion failed: Prometheus should contain gateway HTTP metrics" >&2
    exit 1
  fi

  prometheus_gateway_metric_found=true
else
  echo "Prometheus is not ready. Gateway metric check skipped."
  prometheus_gateway_metric_found=false
fi
echo

echo "### Gateway Smoke Summary"
echo "gateway_health_up=true"
echo "gateway_routing_success=true"
echo "rate_limit_request_count=${RATE_LIMIT_REQUESTS}"
echo "rate_limit_429_count=${rate_limit_429_count}"
echo "fallback_status=${fallback_status}"
echo "backend_recovery_success=true"
echo "prometheus_gateway_metric_found=${prometheus_gateway_metric_found}"

echo
echo "Gateway smoke completed."
