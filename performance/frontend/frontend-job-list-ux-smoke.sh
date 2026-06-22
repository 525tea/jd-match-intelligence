#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-backend}"
ROLE="${ROLE:-BACKEND}"
CAREER_LEVEL="${CAREER_LEVEL:-JUNIOR}"
LOCATION_REGION="${LOCATION_REGION:-Seoul}"
REMOTE_TYPE="${REMOTE_TYPE:-ONSITE}"
SIZE="${SIZE:-20}"

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

assert_json_success() {
  local file="$1"
  local message="$2"
  local success
  success="$(jq -r '.success' "${file}")"
  assert_eq "${success}" "true" "${message}"
}

assert_count_ge() {
  local actual="$1"
  local expected_min="$2"
  local message="$3"
  if (( actual < expected_min )); then
    echo "Assertion failed: ${message}" >&2
    echo "Expected >= ${expected_min}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

request_get() {
  local url="$1"
  local output="$2"
  curl --silent --show-error --fail --get "${url}" --output "${output}" --write-out '%{http_code}' "${@:3}"
}

echo "BASE_URL=${BASE_URL}"
echo "SEARCH_KEYWORD=${SEARCH_KEYWORD}"
echo "ROLE=${ROLE}"
echo "CAREER_LEVEL=${CAREER_LEVEL}"
echo "LOCATION_REGION=${LOCATION_REGION}"
echo "REMOTE_TYPE=${REMOTE_TYPE}"
echo "SIZE=${SIZE}"
echo

echo "### Initial job list should use GET /jobs"
jobs_response="${tmp_dir}/jobs.json"
jobs_status="$(
  request_get "${BASE_URL}/jobs" "${jobs_response}" \
    --data-urlencode "page=0" \
    --data-urlencode "size=${SIZE}"
)"
jobs_count="$(jq '.data | length' "${jobs_response}")"
jobs_has_keyword_param="$(jq 'has("keyword")' "${jobs_response}")"
echo "jobs_status=${jobs_status}"
echo "jobs_count=${jobs_count}"
assert_eq "${jobs_status}" "200" "GET /jobs should return 200"
assert_json_success "${jobs_response}" "GET /jobs should return success=true"
assert_count_ge "${jobs_count}" 1 "GET /jobs should return at least one job"
assert_eq "${jobs_has_keyword_param}" "false" "GET /jobs response should not be a keyword request payload"

echo
echo "### Keyword search should use GET /jobs/search"
search_response="${tmp_dir}/jobs-search.json"
search_status="$(
  request_get "${BASE_URL}/jobs/search" "${search_response}" \
    --data-urlencode "keyword=${SEARCH_KEYWORD}" \
    --data-urlencode "limit=${SIZE}"
)"
search_count="$(jq '.data | length' "${search_response}")"
echo "jobs_search_status=${search_status}"
echo "jobs_search_count=${search_count}"
assert_eq "${search_status}" "200" "GET /jobs/search should return 200"
assert_json_success "${search_response}" "GET /jobs/search should return success=true"
assert_count_ge "${search_count}" 1 "GET /jobs/search should return at least one job"

echo
echo "### Applied filter should use GET /jobs query filters"
filter_response="${tmp_dir}/jobs-filter.json"
filter_status="$(
  request_get "${BASE_URL}/jobs" "${filter_response}" \
    --data-urlencode "page=0" \
    --data-urlencode "size=${SIZE}" \
    --data-urlencode "role=${ROLE}" \
    --data-urlencode "careerLevel=${CAREER_LEVEL}" \
    --data-urlencode "locationRegion=${LOCATION_REGION}" \
    --data-urlencode "remoteType=${REMOTE_TYPE}"
)"
filter_count="$(jq '.data | length' "${filter_response}")"
invalid_role_count="$(jq --arg role "${ROLE}" '[.data[] | select(.role != $role)] | length' "${filter_response}")"
invalid_region_count="$(jq --arg region "${LOCATION_REGION}" '[.data[] | select(.locationRegion != $region)] | length' "${filter_response}")"
invalid_remote_count="$(jq --arg remote "${REMOTE_TYPE}" '[.data[] | select(.remoteType != $remote)] | length' "${filter_response}")"
echo "jobs_filter_status=${filter_status}"
echo "jobs_filter_count=${filter_count}"
echo "invalid_role_count=${invalid_role_count}"
echo "invalid_region_count=${invalid_region_count}"
echo "invalid_remote_count=${invalid_remote_count}"
assert_eq "${filter_status}" "200" "GET /jobs with filters should return 200"
assert_json_success "${filter_response}" "GET /jobs with filters should return success=true"
assert_count_ge "${filter_count}" 1 "GET /jobs with filters should return at least one job"
assert_eq "${invalid_role_count}" "0" "GET /jobs filter response should match role"
assert_eq "${invalid_region_count}" "0" "GET /jobs filter response should match locationRegion"
assert_eq "${invalid_remote_count}" "0" "GET /jobs filter response should match remoteType"

echo
echo "### Frontend Job List UX Smoke Summary"
echo "jobs_status=${jobs_status}"
echo "jobs_count=${jobs_count}"
echo "jobs_search_status=${search_status}"
echo "jobs_search_count=${search_count}"
echo "jobs_filter_status=${filter_status}"
echo "jobs_filter_count=${filter_count}"
echo "invalid_role_count=${invalid_role_count}"
echo "invalid_region_count=${invalid_region_count}"
echo "invalid_remote_count=${invalid_remote_count}"
echo
echo "Frontend job list UX smoke completed."
