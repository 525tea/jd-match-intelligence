#!/usr/bin/env bash

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
ZIPKIN_URL="${ZIPKIN_URL:-http://127.0.0.1:9411}"
SERVICE_NAME="${SERVICE_NAME:-jobflow}"
MONTH="${MONTH:-2026-06-01}"
LIMIT="${LIMIT:-10}"

echo "BACKEND_URL=${BACKEND_URL}"
echo "ZIPKIN_URL=${ZIPKIN_URL}"
echo "SERVICE_NAME=${SERVICE_NAME}"
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

assert_not_empty_json_array() {
  local value="$1"
  local message="$2"

  if [[ "${value}" == "[]" ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Actual: []" >&2
    exit 1
  fi
}

require_command curl

echo "### Warm backend requests"
curl --fail --silent --show-error "${BACKEND_URL}/actuator/health" >/dev/null
curl --fail --silent --show-error \
  "${BACKEND_URL}/trends/skills?periodStart=${MONTH}&limit=${LIMIT}" >/dev/null
curl --fail --silent --show-error \
  --get "${BACKEND_URL}/jobs/search" \
  --data-urlencode "keyword=백엔드" \
  --data-urlencode "limit=5" >/dev/null || true

# Zipkin reporter sends spans asynchronously, so give it a short flush window.
sleep 3

echo "Backend trace warm-up completed."
echo

echo "### Zipkin services"
services_response="$(curl --fail --silent --show-error "${ZIPKIN_URL}/api/v2/services")"
echo "${services_response}"
assert_contains "${services_response}" "\"${SERVICE_NAME}\"" "Zipkin service list should include ${SERVICE_NAME}"
echo

echo "### Zipkin spans"
spans_response="$(curl --fail --silent --show-error \
  --get "${ZIPKIN_URL}/api/v2/spans" \
  --data-urlencode "serviceName=${SERVICE_NAME}")"
echo "${spans_response}"
assert_not_empty_json_array "${spans_response}" "Zipkin spans should not be empty"
echo

echo "### Zipkin traces"
traces_response="$(curl --fail --silent --show-error \
  --get "${ZIPKIN_URL}/api/v2/traces" \
  --data-urlencode "serviceName=${SERVICE_NAME}" \
  --data-urlencode "limit=10" \
  --data-urlencode "lookback=3600000")"
assert_not_empty_json_array "${traces_response}" "Zipkin traces should not be empty"

trace_count="$(echo "${traces_response}" | grep -o '"traceId"' | wc -l | tr -d ' ')"
first_trace_id="$(echo "${traces_response}" | grep -o '"traceId":"[^"]*"' | head -1 | cut -d '"' -f 4 || true)"
span_name_count="$(echo "${traces_response}" | grep -o '"name":"[^"]*"' | wc -l | tr -d ' ')"

echo "trace_count=${trace_count}"
echo "first_trace_id=${first_trace_id}"
echo "span_name_count=${span_name_count}"
echo

echo "### Zipkin UI"
echo "${ZIPKIN_URL}/zipkin/?serviceName=${SERVICE_NAME}&limit=10&lookback=3600000"
echo

echo "### Zipkin Tracing Smoke Summary"
echo "zipkin_service_found=true"
echo "zipkin_trace_found=true"
echo "service_name=${SERVICE_NAME}"
echo "trace_count=${trace_count}"
echo "first_trace_id=${first_trace_id}"
echo "span_name_count=${span_name_count}"

echo
echo "Zipkin tracing smoke completed."
