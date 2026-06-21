#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-backend}"
LIMIT="${LIMIT:-1}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
ADMIN_ACCESS_TOKEN="${ADMIN_ACCESS_TOKEN:-}"
SMOKE_RUN_ID="${SMOKE_RUN_ID:-$(date +%s)}"

echo "BASE_URL=${BASE_URL}"
echo "SEARCH_KEYWORD=${SEARCH_KEYWORD}"
echo "LIMIT=${LIMIT}"
echo "ACCESS_TOKEN=$([ -n "${ACCESS_TOKEN}" ] && echo provided || echo not provided)"
echo "ADMIN_ACCESS_TOKEN=$([ -n "${ADMIN_ACCESS_TOKEN}" ] && echo provided || echo not provided)"
echo "SMOKE_RUN_ID=${SMOKE_RUN_ID}"

assert_eq() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if [ "${actual}" != "${expected}" ]; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected: ${expected}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

assert_contains() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if [[ "${actual}" != *"${expected}"* ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected to contain: ${expected}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

status_of() {
  local method="$1"
  local url="$2"
  shift 2

  curl --silent --output /dev/null --write-out "%{http_code}" \
    --request "${method}" \
    "$@" \
    "${url}"
}

body_of() {
  local method="$1"
  local url="$2"
  shift 2

  curl --silent \
    --request "${method}" \
    "$@" \
    "${url}"
}

capture_response() {
  local method="$1"
  local url="$2"
  local body_file="$3"
  shift 3

  curl --silent --output "${body_file}" --write-out "%{http_code}" \
    --request "${method}" \
    "$@" \
    "${url}"
}

admin_skill_body() {
  cat <<JSON
{
  "name": "Smoke Admin Skill ${SMOKE_RUN_ID}",
  "normalizedName": "smoke-admin-skill-${SMOKE_RUN_ID}",
  "category": "TOOL"
}
JSON
}

echo
echo "### Public API without token"

skills_status="$(status_of GET "${BASE_URL}/skills")"
jobs_search_status="$(status_of GET "${BASE_URL}/jobs/search?keyword=${SEARCH_KEYWORD}&limit=${LIMIT}")"
trend_skills_status="$(status_of GET "${BASE_URL}/trends/skills?limit=${LIMIT}")"

echo "skills_status=${skills_status}"
echo "jobs_search_status=${jobs_search_status}"
echo "trend_skills_status=${trend_skills_status}"

assert_eq "${skills_status}" "200" "GET /skills should be public"
assert_eq "${jobs_search_status}" "200" "GET /jobs/search should be public"
assert_eq "${trend_skills_status}" "200" "GET /trends/skills should be public"

echo
echo "### Protected USER API without token"

auth_me_without_token_body="$(body_of GET "${BASE_URL}/auth/me")"
auth_me_without_token_status="$(status_of GET "${BASE_URL}/auth/me")"

echo "auth_me_without_token_status=${auth_me_without_token_status}"
echo "${auth_me_without_token_body}"

assert_eq "${auth_me_without_token_status}" "401" "GET /auth/me without token should return 401"
assert_contains "${auth_me_without_token_body}" "COMMON_UNAUTHORIZED" "401 response should include COMMON_UNAUTHORIZED"
assert_contains "${auth_me_without_token_body}" "timestamp" "401 response should include timestamp metadata"
assert_contains "${auth_me_without_token_body}" "path" "401 response should include path metadata"

echo
echo "### Admin API without token"

admin_without_token_response_file="$(mktemp)"
admin_without_token_status="$(capture_response POST "${BASE_URL}/skills" "${admin_without_token_response_file}" \
  --header "Content-Type: application/json" \
  --data "$(admin_skill_body)")"
admin_without_token_body="$(cat "${admin_without_token_response_file}")"
rm -f "${admin_without_token_response_file}"

echo "admin_without_token_status=${admin_without_token_status}"
echo "${admin_without_token_body}"

assert_eq "${admin_without_token_status}" "401" "POST /skills without token should return 401"
assert_contains "${admin_without_token_body}" "COMMON_UNAUTHORIZED" "admin 401 response should include COMMON_UNAUTHORIZED"
assert_contains "${admin_without_token_body}" "timestamp" "admin 401 response should include timestamp metadata"
assert_contains "${admin_without_token_body}" "path" "admin 401 response should include path metadata"

echo
echo "### Admin API with USER token"

if [ -n "${ACCESS_TOKEN}" ]; then
  admin_with_user_response_file="$(mktemp)"
  admin_with_user_status="$(capture_response POST "${BASE_URL}/skills" "${admin_with_user_response_file}" \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --header "Content-Type: application/json" \
    --data "$(admin_skill_body)")"
  admin_with_user_body="$(cat "${admin_with_user_response_file}")"
  rm -f "${admin_with_user_response_file}"

  echo "admin_with_user_status=${admin_with_user_status}"
  echo "${admin_with_user_body}"

  assert_eq "${admin_with_user_status}" "403" "POST /skills with USER token should return 403"
  assert_contains "${admin_with_user_body}" "COMMON_FORBIDDEN" "admin 403 response should include COMMON_FORBIDDEN"
  assert_contains "${admin_with_user_body}" "timestamp" "admin 403 response should include timestamp metadata"
  assert_contains "${admin_with_user_body}" "path" "admin 403 response should include path metadata"
else
  admin_with_user_status="skipped"
  echo "admin_with_user_status=skipped"
fi

echo
echo "### Admin API with ADMIN token"

if [ -n "${ADMIN_ACCESS_TOKEN}" ]; then
  admin_with_admin_response_file="$(mktemp)"
  admin_with_admin_status="$(capture_response POST "${BASE_URL}/skills" "${admin_with_admin_response_file}" \
    --header "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
    --header "Content-Type: application/json" \
    --data "$(admin_skill_body)")"
  admin_with_admin_body="$(cat "${admin_with_admin_response_file}")"
  rm -f "${admin_with_admin_response_file}"

  echo "admin_with_admin_status=${admin_with_admin_status}"
  echo "${admin_with_admin_body}"

  assert_eq "${admin_with_admin_status}" "201" "POST /skills with ADMIN token should return 201"
  assert_contains "${admin_with_admin_body}" "\"success\":true" "admin success response should include success=true"
else
  admin_with_admin_status="skipped"
  echo "admin_with_admin_status=skipped"
fi

echo
echo "### API Authorization Matrix Summary"
echo "public_skills_status=${skills_status}"
echo "public_jobs_search_status=${jobs_search_status}"
echo "public_trend_skills_status=${trend_skills_status}"
echo "auth_me_without_token_status=${auth_me_without_token_status}"
echo "admin_without_token_status=${admin_without_token_status}"
echo "admin_with_user_status=${admin_with_user_status}"
echo "admin_with_admin_status=${admin_with_admin_status}"

echo
echo "API authorization matrix smoke completed."
