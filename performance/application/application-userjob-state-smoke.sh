#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
SEARCH_KEYWORD="${SEARCH_KEYWORD:-backend}"
LIMIT="${LIMIT:-5}"

echo "BASE_URL=${BASE_URL}"
echo "SEARCH_KEYWORD=${SEARCH_KEYWORD}"
echo "LIMIT=${LIMIT}"
echo "ACCESS_TOKEN=$([ -n "${ACCESS_TOKEN}" ] && echo provided || echo not provided)"

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required." >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

if [ -z "${ACCESS_TOKEN}" ]; then
  echo "ACCESS_TOKEN is required." >&2
  exit 1
fi

tmp_dir="$(mktemp -d)"
trap 'rm -rf "${tmp_dir}"' EXIT

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

assert_json_success() {
  local file="$1"
  local message="$2"

  if ! jq -e '.success == true' "${file}" >/dev/null; then
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

echo
echo "### GET /auth/me"
auth_me_response="${tmp_dir}/auth-me.json"
auth_me_status="$(request_status "GET" "/auth/me" "${auth_me_response}")"

echo "auth_me_status=${auth_me_status}"
assert_eq "${auth_me_status}" "200" "GET /auth/me should return 200"
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
assert_eq "${search_status}" "200" "GET /jobs/search should return 200"
assert_json_success "${search_response}" "GET /jobs/search should return success=true"

if ! jq -e '(.data | type == "array") and (.data | length > 0)' "${search_response}" >/dev/null; then
  echo "Assertion failed: GET /jobs/search should return at least one job" >&2
  cat "${search_response}" >&2
  exit 1
fi

job_id="$(jq -r '.data[0].id' "${search_response}")"
job_title="$(jq -r '.data[0].title' "${search_response}")"

echo "selected_job_id=${job_id}"
echo "selected_job_title=${job_title}"

echo
echo "### UserJob state flow"

job_detail_response="${tmp_dir}/job-detail.json"
job_detail_status="$(request_status "GET" "/jobs/${job_id}" "${job_detail_response}")"
echo "job_detail_status=${job_detail_status}"
assert_eq "${job_detail_status}" "200" "GET /jobs/{jobId} with token should return 200"
assert_json_success "${job_detail_response}" "GET /jobs/{jobId} should return success=true"

viewed_state_response="${tmp_dir}/user-job-viewed-state.json"
viewed_state_status="$(request_status "GET" "/user/jobs/${job_id}" "${viewed_state_response}")"
echo "user_job_auto_view_status=${viewed_state_status}"
assert_eq "${viewed_state_status}" "200" "GET /jobs/{jobId} should record VIEWED user job state"
assert_json_success "${viewed_state_response}" "GET /user/jobs/{jobId} should return success=true after detail view"
assert_eq "$(jq -r '.data.status' "${viewed_state_response}")" "VIEWED" "detail view should set status=VIEWED"

save_response="${tmp_dir}/user-job-save.json"
save_status="$(request_status "POST" "/user/jobs/${job_id}/save" "${save_response}")"
echo "user_job_save_status=${save_status}"
assert_eq "${save_status}" "200" "POST /user/jobs/{jobId}/save should return 200"
assert_json_success "${save_response}" "POST /user/jobs/{jobId}/save should return success=true"
assert_eq "$(jq -r '.data.status' "${save_response}")" "SAVED" "save should set status=SAVED"

saved_response="${tmp_dir}/user-jobs-saved.json"
saved_status="$(request_status "GET" "/user/jobs/saved?page=0&size=1" "${saved_response}")"
echo "user_jobs_saved_page_status=${saved_status}"
assert_eq "${saved_status}" "200" "GET /user/jobs/saved?page=0&size=1 should return 200"
assert_json_success "${saved_response}" "GET /user/jobs/saved should return success=true"

if ! jq -e '(.data | type == "array") and (.data | length <= 1)' "${saved_response}" >/dev/null; then
  echo "Assertion failed: saved page size should be limited to 1" >&2
  cat "${saved_response}" >&2
  exit 1
fi

unsave_response="${tmp_dir}/user-job-unsave.json"
unsave_status="$(request_status "DELETE" "/user/jobs/${job_id}/save" "${unsave_response}")"
echo "user_job_unsave_status=${unsave_status}"
assert_eq "${unsave_status}" "200" "DELETE /user/jobs/{jobId}/save should return 200"
assert_json_success "${unsave_response}" "DELETE /user/jobs/{jobId}/save should return success=true"
assert_eq "$(jq -r '.data.status' "${unsave_response}")" "VIEWED" "unsave should set status=VIEWED"

ignore_response="${tmp_dir}/user-job-ignore.json"
ignore_status="$(request_status "POST" "/user/jobs/${job_id}/ignore" "${ignore_response}")"
echo "user_job_ignore_status=${ignore_status}"
assert_eq "${ignore_status}" "200" "POST /user/jobs/{jobId}/ignore should return 200"
assert_json_success "${ignore_response}" "POST /user/jobs/{jobId}/ignore should return success=true"
assert_eq "$(jq -r '.data.status' "${ignore_response}")" "IGNORED" "ignore should set status=IGNORED"

ignored_response="${tmp_dir}/user-jobs-ignored.json"
ignored_status="$(request_status "GET" "/user/jobs/ignored?page=0&size=1" "${ignored_response}")"
echo "user_jobs_ignored_page_status=${ignored_status}"
assert_eq "${ignored_status}" "200" "GET /user/jobs/ignored?page=0&size=1 should return 200"
assert_json_success "${ignored_response}" "GET /user/jobs/ignored should return success=true"

if ! jq -e '(.data | type == "array") and (.data | length <= 1)' "${ignored_response}" >/dev/null; then
  echo "Assertion failed: ignored page size should be limited to 1" >&2
  cat "${ignored_response}" >&2
  exit 1
fi

unignore_response="${tmp_dir}/user-job-unignore.json"
unignore_status="$(request_status "DELETE" "/user/jobs/${job_id}/ignore" "${unignore_response}")"
echo "user_job_unignore_status=${unignore_status}"
assert_eq "${unignore_status}" "200" "DELETE /user/jobs/{jobId}/ignore should return 200"
assert_json_success "${unignore_response}" "DELETE /user/jobs/{jobId}/ignore should return success=true"
assert_eq "$(jq -r '.data.status' "${unignore_response}")" "VIEWED" "unignore should set status=VIEWED"

echo
echo "### Application state transition flow"

application_create_response="${tmp_dir}/application-create.json"
application_create_status="$(
  request_status "POST" "/applications" "${application_create_response}" \
    --header "Content-Type: application/json" \
    --data "{\"jobId\":${job_id}}"
)"

echo "application_create_status=${application_create_status}"

if [ "${application_create_status}" = "201" ]; then
  assert_json_success "${application_create_response}" "POST /applications should return success=true"
  application_id="$(jq -r '.data.id' "${application_create_response}")"
elif [ "${application_create_status}" = "409" ]; then
  echo "application_create_conflict=true"
  applications_response="${tmp_dir}/applications.json"
  applications_status="$(request_status "GET" "/applications" "${applications_response}")"
  echo "applications_status=${applications_status}"
  assert_eq "${applications_status}" "200" "GET /applications should return 200"
  assert_json_success "${applications_response}" "GET /applications should return success=true"

  application_id="$(jq -r --argjson jobId "${job_id}" '.data[] | select(.jobId == $jobId) | .id' "${applications_response}" | head -n 1)"

  if [ -z "${application_id}" ]; then
    echo "Assertion failed: existing application for selected job should be found after duplicate conflict" >&2
    cat "${applications_response}" >&2
    exit 1
  fi
else
  echo "Assertion failed: POST /applications should return 201 or duplicate 409" >&2
  cat "${application_create_response}" >&2
  exit 1
fi

echo "application_id=${application_id}"

application_interview_response="${tmp_dir}/application-interview.json"
application_interview_status="$(
  request_status "PATCH" "/applications/${application_id}/status" "${application_interview_response}" \
    --header "Content-Type: application/json" \
    --data '{"status":"INTERVIEW"}'
)"

echo "application_interview_status=${application_interview_status}"
assert_eq "${application_interview_status}" "200" "PATCH /applications/{id}/status to INTERVIEW should return 200"
assert_json_success "${application_interview_response}" "PATCH /applications/{id}/status should return success=true"
assert_eq "$(jq -r '.data.status' "${application_interview_response}")" "INTERVIEW" "application status should be INTERVIEW"

application_histories_response="${tmp_dir}/application-status-histories.json"
application_histories_status="$(
  request_status "GET" "/applications/${application_id}/status-histories" "${application_histories_response}"
)"

echo "application_status_histories_status=${application_histories_status}"
assert_eq "${application_histories_status}" "200" "GET /applications/{id}/status-histories should return 200"
assert_json_success "${application_histories_response}" "GET /applications/{id}/status-histories should return success=true"

if ! jq -e '(.data | type == "array") and (.data | length >= 2)' "${application_histories_response}" >/dev/null; then
  echo "Assertion failed: application status histories should include create and status update records" >&2
  cat "${application_histories_response}" >&2
  exit 1
fi

if ! jq -e '.data | any(.previousStatus == null and .nextStatus == "APPLIED")' "${application_histories_response}" >/dev/null; then
  echo "Assertion failed: application status histories should include initial APPLIED record" >&2
  cat "${application_histories_response}" >&2
  exit 1
fi

if ! jq -e '.data | any(.previousStatus == "APPLIED" and .nextStatus == "INTERVIEW")' "${application_histories_response}" >/dev/null; then
  echo "Assertion failed: application status histories should include APPLIED -> INTERVIEW record" >&2
  cat "${application_histories_response}" >&2
  exit 1
fi

application_invalid_response="${tmp_dir}/application-invalid.json"
application_invalid_status="$(
  request_status "PATCH" "/applications/${application_id}/status" "${application_invalid_response}" \
    --header "Content-Type: application/json" \
    --data '{"status":"DOCUMENT_PASSED"}'
)"

echo "application_invalid_transition_status=${application_invalid_status}"
assert_eq "${application_invalid_status}" "409" "PATCH /applications/{id}/status should reject invalid backward transition"

if ! jq -e '.success == false and .error.code == "APPLICATION_STATUS_CONFLICT"' "${application_invalid_response}" >/dev/null; then
  echo "Assertion failed: invalid transition should return APPLICATION_STATUS_CONFLICT" >&2
  cat "${application_invalid_response}" >&2
  exit 1
fi

echo
echo "### Application/UserJob State Smoke Summary"
echo "user_id=${user_id}"
echo "selected_job_id=${job_id}"
echo "job_detail_status=${job_detail_status}"
echo "user_job_auto_view_status=${viewed_state_status}"
echo "user_job_save_status=${save_status}"
echo "user_job_unsave_status=${unsave_status}"
echo "user_job_ignore_status=${ignore_status}"
echo "user_job_unignore_status=${unignore_status}"
echo "application_id=${application_id}"
echo "application_interview_status=${application_interview_status}"
echo "application_status_histories_status=${application_histories_status}"
echo "application_invalid_transition_status=${application_invalid_status}"

echo
echo "Application/UserJob state smoke completed."
