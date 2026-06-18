#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-backend}"
LIMIT="${LIMIT:-5}"
APPLICATION_STATUS="${APPLICATION_STATUS:-INTERVIEW}"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

if [[ -z "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN is required." >&2
  echo "Get a JobFlow JWT through OAuth, then run ACCESS_TOKEN='...' bash $0" >&2
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

request_public_status() {
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

echo "BASE_URL=${BASE_URL}"
echo "SEARCH_KEYWORD=${SEARCH_KEYWORD}"
echo "LIMIT=${LIMIT}"
echo "APPLICATION_STATUS=${APPLICATION_STATUS}"
echo "ACCESS_TOKEN=provided"
echo

echo "### GET /auth/me"
auth_me_response="${tmp_dir}/auth-me.json"
auth_me_status="$(request_status "GET" "/auth/me" "${auth_me_response}")"

echo "auth_me_status=${auth_me_status}"
assert_equals "${auth_me_status}" "200" "GET /auth/me should return 200"
assert_json_success "${auth_me_response}" "GET /auth/me should return success=true"

user_id="$(jq -r '.data.id' "${auth_me_response}")"
user_email="$(jq -r '.data.email' "${auth_me_response}")"

echo "user_id=${user_id}"
echo "user_email=${user_email}"

echo
echo "### GET /jobs/search"
search_response="${tmp_dir}/jobs-search.json"
search_status="$(
  curl --silent --show-error \
    --get \
    --data-urlencode "keyword=${SEARCH_KEYWORD}" \
    --data-urlencode "limit=${LIMIT}" \
    --output "${search_response}" \
    --write-out "%{http_code}" \
    "${BASE_URL}/jobs/search"
)"

echo "jobs_search_status=${search_status}"
assert_equals "${search_status}" "200" "GET /jobs/search should return 200"
assert_json_success "${search_response}" "GET /jobs/search should return success=true"
assert_data_array_not_empty "${search_response}" "GET /jobs/search should return at least one job"

job_id="$(jq -r '.data[0].id' "${search_response}")"
job_title="$(jq -r '.data[0].title' "${search_response}")"

echo "selected_job_id=${job_id}"
echo "selected_job_title=${job_title}"

echo
echo "### GET /jobs/${job_id}"
job_detail_response="${tmp_dir}/job-detail.json"
job_detail_status="$(request_public_status "GET" "/jobs/${job_id}" "${job_detail_response}")"

echo "job_detail_status=${job_detail_status}"
assert_equals "${job_detail_status}" "200" "GET /jobs/{jobId} should return 200"
assert_json_success "${job_detail_response}" "GET /jobs/{jobId} should return success=true"

detail_job_id="$(jq -r '.data.id' "${job_detail_response}")"
assert_equals "${detail_job_id}" "${job_id}" "Job detail id should match selected search result"

echo
echo "### POST /user/jobs/${job_id}/view"
view_response="${tmp_dir}/user-job-view.json"
view_status="$(request_status "POST" "/user/jobs/${job_id}/view" "${view_response}")"

echo "user_job_view_status=${view_status}"
assert_equals "${view_status}" "200" "POST /user/jobs/{jobId}/view should return 200"
assert_json_success "${view_response}" "POST /user/jobs/{jobId}/view should return success=true"

echo
echo "### POST /user/jobs/${job_id}/save"
save_response="${tmp_dir}/user-job-save.json"
save_status="$(request_status "POST" "/user/jobs/${job_id}/save" "${save_response}")"

echo "user_job_save_status=${save_status}"
assert_equals "${save_status}" "200" "POST /user/jobs/{jobId}/save should return 200"
assert_json_success "${save_response}" "POST /user/jobs/{jobId}/save should return success=true"

echo
echo "### GET /user/jobs/saved"
saved_response="${tmp_dir}/user-jobs-saved.json"
saved_status="$(request_status "GET" "/user/jobs/saved" "${saved_response}")"

echo "user_jobs_saved_status=${saved_status}"
assert_equals "${saved_status}" "200" "GET /user/jobs/saved should return 200"
assert_json_success "${saved_response}" "GET /user/jobs/saved should return success=true"

if ! jq -e --argjson jobId "${job_id}" '.data | any(.jobId == $jobId)' "${saved_response}" >/dev/null; then
  echo "Assertion failed: saved jobs should include selected job_id=${job_id}" >&2
  cat "${saved_response}" >&2
  exit 1
fi

echo
echo "### POST /applications"
application_create_response="${tmp_dir}/application-create.json"
application_create_status="$(
  request_status "POST" "/applications" "${application_create_response}" \
    --header "Content-Type: application/json" \
    --data "{\"jobId\":${job_id}}"
)"

echo "application_create_status=${application_create_status}"

application_id=""

if [[ "${application_create_status}" == "201" ]]; then
  assert_json_success "${application_create_response}" "POST /applications should return success=true"
  application_id="$(jq -r '.data.id' "${application_create_response}")"
elif [[ "${application_create_status}" == "409" ]]; then
  duplicate_code="$(jq -r '.error.code // ""' "${application_create_response}")"
  assert_equals "${duplicate_code}" "APPLICATION_ALREADY_EXISTS" \
    "Duplicate application should return APPLICATION_ALREADY_EXISTS"

  echo "application_already_exists=true"
else
  echo "Assertion failed: POST /applications should return 201 or APPLICATION_ALREADY_EXISTS 409" >&2
  cat "${application_create_response}" >&2
  exit 1
fi

echo
echo "### GET /applications"
applications_response="${tmp_dir}/applications.json"
applications_status="$(request_status "GET" "/applications" "${applications_response}")"

echo "applications_status=${applications_status}"
assert_equals "${applications_status}" "200" "GET /applications should return 200"
assert_json_success "${applications_response}" "GET /applications should return success=true"

if [[ -z "${application_id}" || "${application_id}" == "null" ]]; then
  application_id="$(
    jq -r --argjson jobId "${job_id}" '
      .data
      | map(select(.jobId == $jobId))
      | sort_by(.id)
      | last
      | .id // empty
    ' "${applications_response}"
  )"
fi

if [[ -z "${application_id}" || "${application_id}" == "null" ]]; then
  echo "Assertion failed: applications should include selected job_id=${job_id}" >&2
  cat "${applications_response}" >&2
  exit 1
fi

echo "application_id=${application_id}"

echo
echo "### GET /applications/${application_id}"
application_detail_response="${tmp_dir}/application-detail.json"
application_detail_status="$(request_status "GET" "/applications/${application_id}" "${application_detail_response}")"

echo "application_detail_status=${application_detail_status}"
assert_equals "${application_detail_status}" "200" "GET /applications/{applicationId} should return 200"
assert_json_success "${application_detail_response}" "GET /applications/{applicationId} should return success=true"

echo
echo "### PATCH /applications/${application_id}/status"
application_update_response="${tmp_dir}/application-update.json"
application_update_status="$(
  request_status "PATCH" "/applications/${application_id}/status" "${application_update_response}" \
    --header "Content-Type: application/json" \
    --data "{\"status\":\"${APPLICATION_STATUS}\"}"
)"

echo "application_update_status=${application_update_status}"
assert_equals "${application_update_status}" "200" "PATCH /applications/{applicationId}/status should return 200"
assert_json_success "${application_update_response}" "PATCH /applications/{applicationId}/status should return success=true"

updated_status="$(jq -r '.data.status' "${application_update_response}")"
assert_equals "${updated_status}" "${APPLICATION_STATUS}" "Application status should be updated"

echo
echo "### Core API Acceptance Summary"
echo "user_id=${user_id}"
echo "selected_job_id=${job_id}"
echo "application_id=${application_id}"
echo "application_status=${updated_status}"
echo "saved_job_verified=true"
echo "application_flow_verified=true"

echo
echo "Core API acceptance smoke completed."
