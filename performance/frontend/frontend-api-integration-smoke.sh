#!/usr/bin/env bash

set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:5173}"
API_BASE_PATH="${API_BASE_PATH:-/api}"
MONTH="${MONTH:-2026-06-01}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-backend}"
LIMIT="${LIMIT:-1}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"

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

api_url="${FRONTEND_URL%/}${API_BASE_PATH}"

assert_equals() {
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

assert_json_success() {
  local file="$1"
  local message="$2"

  if ! jq -e '.success == true' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

assert_data_array_not_empty() {
  local file="$1"
  local message="$2"

  if ! jq -e '(.data | type == "array") and (.data | length > 0)' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

request_status() {
  local method="$1"
  local url="$2"
  local output_file="$3"
  shift 3

  curl --silent --show-error \
    --request "${method}" \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    "$@" \
    "${url}"
}

echo "FRONTEND_URL=${FRONTEND_URL}"
echo "API_BASE_PATH=${API_BASE_PATH}"
echo "API_PROXY_URL=${api_url}"
echo "MONTH=${MONTH}"
echo "SEARCH_KEYWORD=${SEARCH_KEYWORD}"
echo "LIMIT=${LIMIT}"
if [[ -n "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN=provided"
else
  echo "ACCESS_TOKEN=not provided"
fi
echo

echo "### Frontend document"
frontend_html="$(curl --fail --silent --show-error "${FRONTEND_URL}")"
assert_contains "${frontend_html}" '<div id="root"></div>' "Frontend HTML should expose React root"
assert_contains "${frontend_html}" '/src/main.jsx' "Frontend HTML should load React entrypoint"
echo "frontend_html_ok=true"
echo

echo "### Vite proxy public API"
jobs_response="${tmp_dir}/jobs-search.json"
jobs_status="$(
  curl --silent --show-error \
    --get \
    --data-urlencode "keyword=${SEARCH_KEYWORD}" \
    --data-urlencode "limit=${LIMIT}" \
    --output "${jobs_response}" \
    --write-out "%{http_code}" \
    "${api_url}/jobs/search"
)"

echo "jobs_search_status=${jobs_status}"
assert_equals "${jobs_status}" "200" "Frontend proxy GET /api/jobs/search should return 200"
assert_json_success "${jobs_response}" "Frontend proxy GET /api/jobs/search should return success=true"
assert_data_array_not_empty "${jobs_response}" "Frontend proxy GET /api/jobs/search should return at least one job"

job_id="$(jq -r '.data[0].id' "${jobs_response}")"

job_detail_response="${tmp_dir}/job-detail.json"
job_detail_status="$(request_status "GET" "${api_url}/jobs/${job_id}" "${job_detail_response}")"

echo "job_detail_status=${job_detail_status}"
assert_equals "${job_detail_status}" "200" "Frontend proxy GET /api/jobs/{jobId} should return 200"
assert_json_success "${job_detail_response}" "Frontend proxy GET /api/jobs/{jobId} should return success=true"

if ! jq -e '.data | has("originalUrl")' "${job_detail_response}" >/dev/null; then
  echo "Assertion failed: Frontend proxy job detail should expose originalUrl field" >&2
  cat "${job_detail_response}" >&2
  exit 1
fi

if ! jq -e '(.data.descriptionSections | type == "array") and (.data.descriptionSections | length > 0)' "${job_detail_response}" >/dev/null; then
  echo "Assertion failed: Frontend proxy job detail should expose structured description sections" >&2
  cat "${job_detail_response}" >&2
  exit 1
fi

description_section_count="$(jq -r '.data.descriptionSections | length' "${job_detail_response}")"
echo "job_detail_description_section_count=${description_section_count}"

trends_response="${tmp_dir}/trends-skills.json"
trends_status="$(
  curl --silent --show-error \
    --get \
    --data-urlencode "month=${MONTH}" \
    --data-urlencode "limit=${LIMIT}" \
    --output "${trends_response}" \
    --write-out "%{http_code}" \
    "${api_url}/trends/skills"
)"

echo "trend_skills_status=${trends_status}"
assert_equals "${trends_status}" "200" "Frontend proxy GET /api/trends/skills should return 200"
assert_json_success "${trends_response}" "Frontend proxy GET /api/trends/skills should return success=true"
assert_data_array_not_empty "${trends_response}" "Frontend proxy GET /api/trends/skills should return at least one skill"
echo

echo "### Vite proxy auth boundary"
auth_me_response="${tmp_dir}/auth-me-without-token.json"
auth_me_without_token_status="$(request_status "GET" "${api_url}/auth/me" "${auth_me_response}")"

echo "auth_me_without_token_status=${auth_me_without_token_status}"
assert_equals "${auth_me_without_token_status}" "401" "Frontend proxy GET /api/auth/me should return 401 without token"

auth_me_with_token_status="skipped"

if [[ -n "${ACCESS_TOKEN}" ]]; then
  auth_me_with_token_response="${tmp_dir}/auth-me-with-token.json"
  auth_me_with_token_status="$(
    curl --silent --show-error \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --output "${auth_me_with_token_response}" \
      --write-out "%{http_code}" \
      "${api_url}/auth/me"
  )"

  echo "auth_me_with_token_status=${auth_me_with_token_status}"
  assert_equals "${auth_me_with_token_status}" "200" "Frontend proxy GET /api/auth/me should return 200 with token"
  assert_json_success "${auth_me_with_token_response}" "Frontend proxy GET /api/auth/me should return success=true with token"
fi

echo
echo "### Frontend API Integration Smoke Summary"
echo "frontend_html_ok=true"
echo "jobs_search_status=200"
echo "job_detail_status=200"
echo "job_detail_description_section_count=${description_section_count}"
echo "trend_skills_status=200"
echo "auth_me_without_token_status=401"
echo "auth_me_with_token_status=${auth_me_with_token_status}"

echo
echo "Frontend API integration smoke completed."
