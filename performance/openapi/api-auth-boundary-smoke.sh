#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
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

assert_json_success() {
  local file="$1"
  local message="$2"

  if ! jq -e '.success == true' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

assert_error_code() {
  local file="$1"
  local expected_code="$2"
  local message="$3"

  local actual_code
  actual_code="$(jq -r '.error.code // ""' "${file}")"

  if [[ "${actual_code}" != "${expected_code}" ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected error code: ${expected_code}" >&2
    echo "Actual response:" >&2
    cat "${file}" >&2
    exit 1
  fi
}

request_status() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  shift 3

  curl --silent --show-error \
    --request "${method}" \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    "$@" \
    "${BASE_URL}${path}"
}

request_status_with_token() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  shift 3

  curl --silent --show-error \
    --request "${method}" \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    "$@" \
    "${BASE_URL}${path}"
}

check_public_get() {
  local path="$1"
  local label="$2"
  local response_file="${tmp_dir}/${label}.json"
  local status

  status="$(request_status "GET" "${path}" "${response_file}")"

  echo "${label}_status=${status}"
  assert_equals "${status}" "200" "Public endpoint ${path} should return 200"
  assert_json_success "${response_file}" "Public endpoint ${path} should return success=true"
}

check_protected_without_token() {
  local method="$1"
  local path="$2"
  local label="$3"
  local response_file="${tmp_dir}/${label}.json"
  local status

  status="$(request_status "${method}" "${path}" "${response_file}")"

  echo "${label}_without_token_status=${status}"
  assert_equals "${status}" "401" "Protected endpoint ${method} ${path} should return 401 without token"
  assert_error_code "${response_file}" "COMMON_UNAUTHORIZED" \
    "Protected endpoint ${method} ${path} should return COMMON_UNAUTHORIZED without token"
}

echo "BASE_URL=${BASE_URL}"
echo "MONTH=${MONTH}"
echo "SEARCH_KEYWORD=${SEARCH_KEYWORD}"
echo "LIMIT=${LIMIT}"
if [[ -n "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN=provided"
else
  echo "ACCESS_TOKEN=not provided"
fi
echo

echo "### Public API without token"
check_public_get "/jobs/search?keyword=${SEARCH_KEYWORD}&limit=${LIMIT}" "jobs_search"
check_public_get "/trends/skills?month=${MONTH}&limit=${LIMIT}" "trend_skills"

echo
echo "### Protected API without token"
check_protected_without_token "GET" "/auth/me" "auth_me"
check_protected_without_token "GET" "/applications" "applications"
check_protected_without_token "GET" "/recommendations/jobs?limit=${LIMIT}" "recommendations"
check_protected_without_token "GET" "/projects/1/skills" "project_skills"
check_protected_without_token "GET" "/projects/1/experience-tags" "project_experience_tags"
check_protected_without_token "GET" "/projects/1/job-matches?limit=${LIMIT}" "project_job_matches"
check_protected_without_token "GET" "/gap-analysis/projects/1" "gap_analysis"
check_protected_without_token "POST" "/user/jobs/1/save" "user_job_save"

authenticated_auth_me_status="skipped"

if [[ -n "${ACCESS_TOKEN}" ]]; then
  echo
  echo "### Protected API with token"
  auth_me_response="${tmp_dir}/auth_me_with_token.json"
  authenticated_auth_me_status="$(request_status_with_token "GET" "/auth/me" "${auth_me_response}")"

  echo "auth_me_with_token_status=${authenticated_auth_me_status}"
  assert_equals "${authenticated_auth_me_status}" "200" "GET /auth/me should return 200 with token"
  assert_json_success "${auth_me_response}" "GET /auth/me should return success=true with token"
fi

echo
echo "### API Auth Boundary Summary"
echo "public_jobs_search_status=200"
echo "public_trend_skills_status=200"
echo "protected_without_token_status=401"
echo "protected_without_token_error=COMMON_UNAUTHORIZED"
echo "auth_me_with_token_status=${authenticated_auth_me_status}"

echo
echo "API auth boundary smoke completed."
