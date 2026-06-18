#!/usr/bin/env bash

set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://127.0.0.1:5173}"
API_BASE_PATH="${API_BASE_PATH:-/api}"
MONTH="${MONTH:-2026-06-01}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-backend}"
LIMIT="${LIMIT:-1}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
SMOKE_RUN_ID="${SMOKE_RUN_ID:-$(date +%s)}"
FRONTEND_SMOKE_CLIENT_KEY="${FRONTEND_SMOKE_CLIENT_KEY:-frontend-api-smoke-${SMOKE_RUN_ID}}"

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

assert_json_array_path_not_empty() {
  local file="$1"
  local jq_path="$2"
  local message="$3"

  if ! jq -e "(${jq_path} | type == \"array\") and (${jq_path} | length > 0)" "${file}" >/dev/null; then
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
echo "USER_PROJECT_ID=${USER_PROJECT_ID:-not provided}"
if [[ -n "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN=provided"
else
  echo "ACCESS_TOKEN=not provided"
fi
echo "FRONTEND_SMOKE_CLIENT_KEY=${FRONTEND_SMOKE_CLIENT_KEY}"
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
    --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
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
job_detail_status="$(request_status "GET" "${api_url}/jobs/${job_id}" "${job_detail_response}" --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}")"

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
    --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
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
auth_me_without_token_status="$(request_status "GET" "${api_url}/auth/me" "${auth_me_response}" --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}")"

echo "auth_me_without_token_status=${auth_me_without_token_status}"
assert_equals "${auth_me_without_token_status}" "401" "Frontend proxy GET /api/auth/me should return 401 without token"

auth_me_with_token_status="skipped"
project_skills_with_token_status="skipped"
project_job_matches_with_token_status="skipped"
gap_analysis_with_token_status="skipped"
recommendations_with_token_status="skipped"
project_skill_count="skipped"
project_job_match_count="skipped"
gap_job_match_count="skipped"
recommendation_count="skipped"
saved_jobs_with_token_status="skipped"
applications_with_token_status="skipped"

if [[ -n "${ACCESS_TOKEN}" ]]; then
  auth_me_with_token_response="${tmp_dir}/auth-me-with-token.json"
  auth_me_with_token_status="$(
    curl --silent --show-error \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
      --output "${auth_me_with_token_response}" \
      --write-out "%{http_code}" \
      "${api_url}/auth/me"
  )"

  echo "auth_me_with_token_status=${auth_me_with_token_status}"
  assert_equals "${auth_me_with_token_status}" "200" "Frontend proxy GET /api/auth/me should return 200 with token"
  assert_json_success "${auth_me_with_token_response}" "Frontend proxy GET /api/auth/me should return success=true with token"

  echo
  echo "### Vite proxy protected API with token"

  saved_jobs_with_token_response="${tmp_dir}/saved-jobs-with-token.json"
  saved_jobs_with_token_status="$(
    curl --silent --show-error \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
      --output "${saved_jobs_with_token_response}" \
      --write-out "%{http_code}" \
      "${api_url}/user/jobs/saved"
  )"
  echo "saved_jobs_with_token_status=${saved_jobs_with_token_status}"
  assert_equals "${saved_jobs_with_token_status}" "200" "Frontend proxy GET /api/user/jobs/saved should return 200 with token"
  assert_json_success "${saved_jobs_with_token_response}" "Frontend proxy GET /api/user/jobs/saved should return success=true with token"

  applications_with_token_response="${tmp_dir}/applications-with-token.json"
  applications_with_token_status="$(
    curl --silent --show-error \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
      --output "${applications_with_token_response}" \
      --write-out "%{http_code}" \
      "${api_url}/applications"
  )"
  echo "applications_with_token_status=${applications_with_token_status}"
  assert_equals "${applications_with_token_status}" "200" "Frontend proxy GET /api/applications should return 200 with token"
  assert_json_success "${applications_with_token_response}" "Frontend proxy GET /api/applications should return success=true with token"

  if [[ -n "${USER_PROJECT_ID}" ]]; then
    project_skills_with_token_response="${tmp_dir}/project-skills-with-token.json"
    project_skills_with_token_status="$(
      curl --silent --show-error \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
        --output "${project_skills_with_token_response}" \
        --write-out "%{http_code}" \
        "${api_url}/projects/${USER_PROJECT_ID}/skills"
    )"
    echo "project_skills_with_token_status=${project_skills_with_token_status}"
    assert_equals "${project_skills_with_token_status}" "200" "Frontend proxy GET /api/projects/{id}/skills should return 200 with token"
    assert_json_success "${project_skills_with_token_response}" "Frontend proxy GET /api/projects/{id}/skills should return success=true with token"
    assert_data_array_not_empty "${project_skills_with_token_response}" "Frontend proxy GET /api/projects/{id}/skills should return at least one skill"
    project_skill_count="$(jq -r '.data | length' "${project_skills_with_token_response}")"
    echo "project_skill_count=${project_skill_count}"

    project_job_matches_with_token_response="${tmp_dir}/project-job-matches-with-token.json"
    project_job_matches_with_token_status="$(
      curl --silent --show-error \
        --get \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
        --data-urlencode "targetRoles=BACKEND" \
        --data-urlencode "targetRoles=FULLSTACK" \
        --data-urlencode "targetCareerLevel=MID" \
        --data-urlencode "limit=${LIMIT}" \
        --output "${project_job_matches_with_token_response}" \
        --write-out "%{http_code}" \
        "${api_url}/projects/${USER_PROJECT_ID}/job-matches"
    )"
    echo "project_job_matches_with_token_status=${project_job_matches_with_token_status}"
    assert_equals "${project_job_matches_with_token_status}" "200" "Frontend proxy GET /api/projects/{id}/job-matches should return 200 with token"
    assert_json_success "${project_job_matches_with_token_response}" "Frontend proxy GET /api/projects/{id}/job-matches should return success=true with token"
    assert_data_array_not_empty "${project_job_matches_with_token_response}" "Frontend proxy GET /api/projects/{id}/job-matches should return at least one match"
    project_job_match_count="$(jq -r '.data | length' "${project_job_matches_with_token_response}")"
    echo "project_job_match_count=${project_job_match_count}"

    gap_analysis_with_token_response="${tmp_dir}/gap-analysis-with-token.json"
    gap_analysis_with_token_status="$(
      curl --silent --show-error \
        --get \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
        --data-urlencode "targetRoles=BACKEND" \
        --data-urlencode "limit=${LIMIT}" \
        --output "${gap_analysis_with_token_response}" \
        --write-out "%{http_code}" \
        "${api_url}/gap-analysis/projects/${USER_PROJECT_ID}"
    )"
    echo "gap_analysis_with_token_status=${gap_analysis_with_token_status}"
    assert_equals "${gap_analysis_with_token_status}" "200" "Frontend proxy GET /api/gap-analysis/projects/{id} should return 200 with token"
    assert_json_success "${gap_analysis_with_token_response}" "Frontend proxy GET /api/gap-analysis/projects/{id} should return success=true with token"
    assert_json_array_path_not_empty "${gap_analysis_with_token_response}" ".data.jobMatches" "Frontend proxy GET /api/gap-analysis/projects/{id} should return at least one gap match"
    gap_job_match_count="$(jq -r '.data.jobMatches | length' "${gap_analysis_with_token_response}")"
    echo "gap_job_match_count=${gap_job_match_count}"

    recommendations_with_token_response="${tmp_dir}/recommendations-with-token.json"
    recommendations_with_token_status="$(
      curl --silent --show-error \
        --get \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        --header "X-Forwarded-For: ${FRONTEND_SMOKE_CLIENT_KEY}" \
        --data-urlencode "userProjectId=${USER_PROJECT_ID}" \
        --data-urlencode "targetRoles=BACKEND" \
        --data-urlencode "targetRoles=FULLSTACK" \
        --data-urlencode "limit=${LIMIT}" \
        --output "${recommendations_with_token_response}" \
        --write-out "%{http_code}" \
        "${api_url}/recommendations/jobs"
    )"
    echo "recommendations_with_token_status=${recommendations_with_token_status}"
    assert_equals "${recommendations_with_token_status}" "200" "Frontend proxy GET /api/recommendations/jobs should return 200 with token"
    assert_json_success "${recommendations_with_token_response}" "Frontend proxy GET /api/recommendations/jobs should return success=true with token"
    assert_data_array_not_empty "${recommendations_with_token_response}" "Frontend proxy GET /api/recommendations/jobs should return at least one recommendation"
    recommendation_count="$(jq -r '.data | length' "${recommendations_with_token_response}")"
    echo "recommendation_count=${recommendation_count}"
  else
    echo "USER_PROJECT_ID is not provided. Project analytics protected checks are skipped."
  fi
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
echo "saved_jobs_with_token_status=${saved_jobs_with_token_status}"
echo "applications_with_token_status=${applications_with_token_status}"
echo "project_skills_with_token_status=${project_skills_with_token_status}"
echo "project_job_matches_with_token_status=${project_job_matches_with_token_status}"
echo "gap_analysis_with_token_status=${gap_analysis_with_token_status}"
echo "recommendations_with_token_status=${recommendations_with_token_status}"
echo "project_skill_count=${project_skill_count}"
echo "project_job_match_count=${project_job_match_count}"
echo "gap_job_match_count=${gap_job_match_count}"
echo "recommendation_count=${recommendation_count}"

echo
echo "Frontend API integration smoke completed."
