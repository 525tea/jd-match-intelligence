#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
PAGE="${PAGE:-0}"
SIZE="${SIZE:-5}"
PAGED_SIZE="${PAGED_SIZE:-1}"
ROLE="${ROLE:-BACKEND}"
STATUS="${STATUS:-OPEN}"
LOCATION_REGION="${LOCATION_REGION:-Seoul}"
REMOTE_TYPE="${REMOTE_TYPE:-ONSITE}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

assert_eq() {
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

assert_gte() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if (( actual < expected )); then
    echo "Assertion failed: ${message}" >&2
    echo "Expected at least: ${expected}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

assert_json_success() {
  local file="$1"
  local message="$2"

  if ! jq -e '.success == true' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

request_get_status() {
  local output_file="$1"
  shift

  curl --silent --show-error \
    --get \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    "$@" \
    "${BASE_URL}/jobs"
}

assert_all_field_equals() {
  local file="$1"
  local field="$2"
  local expected="$3"
  local message="$4"
  local mismatch_count

  mismatch_count="$(
    jq -r --arg field "${field}" --arg expected "${expected}" '
      [.data[] | select(.[$field] != $expected)] | length
    ' "${file}"
  )"

  assert_eq "${mismatch_count}" "0" "${message}"
}

echo "BASE_URL=${BASE_URL}"
echo "PAGE=${PAGE}"
echo "SIZE=${SIZE}"
echo "PAGED_SIZE=${PAGED_SIZE}"
echo "ROLE=${ROLE}"
echo "STATUS=${STATUS}"
echo "LOCATION_REGION=${LOCATION_REGION}"
echo "REMOTE_TYPE=${REMOTE_TYPE}"

echo
echo "### GET /jobs pagination"
paged_response="${tmp_dir}/jobs-page.json"
paged_status="$(
  request_get_status "${paged_response}" \
    --data-urlencode "page=${PAGE}" \
    --data-urlencode "size=${PAGED_SIZE}"
)"

paged_count="$(jq -r '.data | length' "${paged_response}")"

echo "jobs_page_status=${paged_status}"
echo "jobs_page_count=${paged_count}"

assert_eq "${paged_status}" "200" "GET /jobs with page and size should return 200"
assert_json_success "${paged_response}" "GET /jobs with page and size should return success=true"
assert_gte "${paged_count}" "1" "GET /jobs should return at least one job"
assert_eq "${paged_count}" "${PAGED_SIZE}" "GET /jobs should respect size parameter"

echo
echo "### GET /jobs role/status filter"
role_status_response="${tmp_dir}/jobs-role-status.json"
role_status_http_status="$(
  request_get_status "${role_status_response}" \
    --data-urlencode "page=${PAGE}" \
    --data-urlencode "size=${SIZE}" \
    --data-urlencode "role=${ROLE}" \
    --data-urlencode "status=${STATUS}"
)"

role_status_count="$(jq -r '.data | length' "${role_status_response}")"

echo "jobs_role_status_filter_status=${role_status_http_status}"
echo "jobs_role_status_filter_count=${role_status_count}"

assert_eq "${role_status_http_status}" "200" "GET /jobs role/status filter should return 200"
assert_json_success "${role_status_response}" "GET /jobs role/status filter should return success=true"
assert_gte "${role_status_count}" "1" "GET /jobs role/status filter should return at least one job"
assert_all_field_equals "${role_status_response}" "role" "${ROLE}" "GET /jobs should only return requested role"
assert_all_field_equals "${role_status_response}" "status" "${STATUS}" "GET /jobs should only return requested status"

echo
echo "### GET /jobs location filter"
location_response="${tmp_dir}/jobs-location.json"
location_status="$(
  request_get_status "${location_response}" \
    --data-urlencode "page=${PAGE}" \
    --data-urlencode "size=${SIZE}" \
    --data-urlencode "locationRegion=${LOCATION_REGION}"
)"

location_count="$(jq -r '.data | length' "${location_response}")"

echo "jobs_location_filter_status=${location_status}"
echo "jobs_location_filter_count=${location_count}"

assert_eq "${location_status}" "200" "GET /jobs location filter should return 200"
assert_json_success "${location_response}" "GET /jobs location filter should return success=true"

if (( location_count > 0 )); then
  assert_all_field_equals "${location_response}" "locationRegion" "${LOCATION_REGION}" \
    "GET /jobs should only return requested locationRegion"
fi

echo
echo "### GET /jobs remote filter"
remote_response="${tmp_dir}/jobs-remote.json"
remote_status="$(
  request_get_status "${remote_response}" \
    --data-urlencode "page=${PAGE}" \
    --data-urlencode "size=${SIZE}" \
    --data-urlencode "remoteType=${REMOTE_TYPE}"
)"

remote_count="$(jq -r '.data | length' "${remote_response}")"

echo "jobs_remote_filter_status=${remote_status}"
echo "jobs_remote_filter_count=${remote_count}"

assert_eq "${remote_status}" "200" "GET /jobs remote filter should return 200"
assert_json_success "${remote_response}" "GET /jobs remote filter should return success=true"

if (( remote_count > 0 )); then
  assert_all_field_equals "${remote_response}" "remoteType" "${REMOTE_TYPE}" \
    "GET /jobs should only return requested remoteType"
fi

echo
echo "### GET /jobs validation"
invalid_size_response="${tmp_dir}/jobs-invalid-size.json"
invalid_size_status="$(
  request_get_status "${invalid_size_response}" \
    --data-urlencode "page=0" \
    --data-urlencode "size=101"
)"

echo "jobs_invalid_size_status=${invalid_size_status}"

assert_eq "${invalid_size_status}" "400" "GET /jobs size over 100 should return 400"

echo
echo "### Job List Filter Smoke Summary"
echo "jobs_page_status=${paged_status}"
echo "jobs_page_count=${paged_count}"
echo "jobs_role_status_filter_status=${role_status_http_status}"
echo "jobs_role_status_filter_count=${role_status_count}"
echo "jobs_location_filter_status=${location_status}"
echo "jobs_location_filter_count=${location_count}"
echo "jobs_remote_filter_status=${remote_status}"
echo "jobs_remote_filter_count=${remote_count}"
echo "jobs_invalid_size_status=${invalid_size_status}"

echo
echo "Job list filter smoke completed."
