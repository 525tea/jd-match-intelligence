#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK}"
LIMIT="${LIMIT:-5}"
MISSING_PROJECT_ID="${MISSING_PROJECT_ID:-999999999}"

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
  exit 1
fi

if [[ -z "${USER_PROJECT_ID}" ]]; then
  echo "USER_PROJECT_ID is required." >&2
  echo "Use a project id owned by the authenticated user." >&2
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

assert_array_response() {
  local file="$1"
  local message="$2"

  if ! jq -e '(.data | type == "array")' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

assert_array_not_empty() {
  local file="$1"
  local message="$2"

  if ! jq -e '(.data | type == "array") and (.data | length > 0)' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

assert_project_rows_match() {
  local file="$1"
  local message="$2"

  if ! jq -e --argjson userProjectId "${USER_PROJECT_ID}" '
    [.data[] | select(.userProjectId != $userProjectId)] | length == 0
  ' "${file}" >/dev/null; then
    echo "Assertion failed: ${message}" >&2
    cat "${file}" >&2
    exit 1
  fi
}

assert_missing_project() {
  local file="$1"
  local status="$2"
  local label="$3"

  echo "${label}_missing_project_status=${status}"
  assert_equals "${status}" "404" "${label} missing project should return 404"

  if ! jq -e '.success == false and .error.code == "USER_PROJECT_NOT_FOUND"' "${file}" >/dev/null; then
    echo "Assertion failed: ${label} missing project should return USER_PROJECT_NOT_FOUND" >&2
    cat "${file}" >&2
    exit 1
  fi
}

request_get_status() {
  local path="$1"
  local output_file="$2"
  shift 2

  curl --silent --show-error \
    --get \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --output "${output_file}" \
    --write-out "%{http_code}" \
    "$@" \
    "${BASE_URL}${path}"
}

add_target_roles_args() {
  if [[ -n "${TARGET_ROLES}" ]]; then
    IFS=',' read -ra roles <<< "${TARGET_ROLES}"
    for role in "${roles[@]}"; do
      role="${role#"${role%%[![:space:]]*}"}"
      role="${role%"${role##*[![:space:]]}"}"
      if [[ -n "${role}" ]]; then
        printf '%s\n' "--data-urlencode"
        printf '%s\n' "targetRoles=${role}"
      fi
    done
  fi
}

echo "BASE_URL=${BASE_URL}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
echo "TARGET_ROLES=${TARGET_ROLES}"
echo "LIMIT=${LIMIT}"
echo "ACCESS_TOKEN=provided"
echo

echo "### GET /projects/${USER_PROJECT_ID}/skills"
project_skills_response="${tmp_dir}/project-skills.json"
project_skills_status="$(request_get_status "/projects/${USER_PROJECT_ID}/skills" "${project_skills_response}")"

echo "project_skills_status=${project_skills_status}"
assert_equals "${project_skills_status}" "200" "GET /projects/{userProjectId}/skills should return 200"
assert_json_success "${project_skills_response}" "Project skills should return success=true"
assert_array_not_empty "${project_skills_response}" "Project skills should not be empty"
assert_project_rows_match "${project_skills_response}" "Project skills should only contain requested userProjectId"

skill_count="$(jq '.data | length' "${project_skills_response}")"

echo
echo "### GET /projects/${USER_PROJECT_ID}/experience-tags"
project_tags_response="${tmp_dir}/project-experience-tags.json"
project_tags_status="$(request_get_status "/projects/${USER_PROJECT_ID}/experience-tags" "${project_tags_response}")"

echo "project_experience_tags_status=${project_tags_status}"
assert_equals "${project_tags_status}" "200" "GET /projects/{userProjectId}/experience-tags should return 200"
assert_json_success "${project_tags_response}" "Project experience tags should return success=true"
assert_array_response "${project_tags_response}" "Project experience tags should return an array"
assert_project_rows_match "${project_tags_response}" "Project experience tags should only contain requested userProjectId"

experience_tag_count="$(jq '.data | length' "${project_tags_response}")"

echo
echo "### GET /projects/${USER_PROJECT_ID}/job-matches"
jd_match_response="${tmp_dir}/jd-matches.json"
jd_match_args=(
  --data-urlencode "limit=${LIMIT}"
)

while IFS= read -r arg; do
  jd_match_args+=("${arg}")
done < <(add_target_roles_args)

jd_match_status="$(request_get_status "/projects/${USER_PROJECT_ID}/job-matches" "${jd_match_response}" "${jd_match_args[@]}")"

echo "jd_match_status=${jd_match_status}"
assert_equals "${jd_match_status}" "200" "GET /projects/{userProjectId}/job-matches should return 200"
assert_json_success "${jd_match_response}" "JD match should return success=true"
assert_array_not_empty "${jd_match_response}" "JD match should not be empty"

if ! jq -e '
  [.data[]
   | select(
       .jobId == null
       or .title == null
       or .companyName == null
       or .role == null
       or .score.totalScore == null
       or .matchedRequiredSkills == null
       or .missingRequiredSkills == null
     )
  ] | length == 0
' "${jd_match_response}" >/dev/null; then
  echo "Assertion failed: JD match response has missing required fields" >&2
  cat "${jd_match_response}" >&2
  exit 1
fi

jd_match_count="$(jq '.data | length' "${jd_match_response}")"
top_jd_match_job_id="$(jq -r '.data[0].jobId' "${jd_match_response}")"
top_jd_match_score="$(jq -r '.data[0].score.totalScore' "${jd_match_response}")"

echo
echo "### GET /gap-analysis/projects/${USER_PROJECT_ID}"
gap_response="${tmp_dir}/gap-analysis.json"
gap_args=(
  --data-urlencode "limit=${LIMIT}"
)

while IFS= read -r arg; do
  gap_args+=("${arg}")
done < <(add_target_roles_args)

gap_status="$(request_get_status "/gap-analysis/projects/${USER_PROJECT_ID}" "${gap_response}" "${gap_args[@]}")"

echo "gap_analysis_status=${gap_status}"
assert_equals "${gap_status}" "200" "GET /gap-analysis/projects/{userProjectId} should return 200"
assert_json_success "${gap_response}" "Gap analysis should return success=true"

if ! jq -e --argjson userProjectId "${USER_PROJECT_ID}" '
  .data.userProjectId == $userProjectId
  and (.data.userSkillIds | type == "array")
  and (.data.jobMatches | type == "array")
  and (.data.jobMatches | length > 0)
' "${gap_response}" >/dev/null; then
  echo "Assertion failed: Gap analysis response has invalid shape" >&2
  cat "${gap_response}" >&2
  exit 1
fi

gap_match_count="$(jq '.data.jobMatches | length' "${gap_response}")"
gap_user_skill_count="$(jq '.data.userSkillIds | length' "${gap_response}")"

echo
echo "### GET /recommendations/jobs"
recommendations_response="${tmp_dir}/recommendations.json"
recommendation_args=(
  --data-urlencode "userProjectId=${USER_PROJECT_ID}"
  --data-urlencode "limit=${LIMIT}"
)

while IFS= read -r arg; do
  recommendation_args+=("${arg}")
done < <(add_target_roles_args)

recommendation_status="$(request_get_status "/recommendations/jobs" "${recommendations_response}" "${recommendation_args[@]}")"

echo "recommendations_status=${recommendation_status}"
assert_equals "${recommendation_status}" "200" "GET /recommendations/jobs should return 200"
assert_json_success "${recommendations_response}" "Recommendations should return success=true"
assert_array_not_empty "${recommendations_response}" "Recommendations should not be empty"

if ! jq -e '
  [.data[]
   | select(
       .jobId == null
       or .title == null
       or .companyName == null
       or .role == null
       or .score.totalScore == null
       or .score.skillMatchScore == null
       or .score.freshnessScore == null
       or .score.behaviorScore == null
       or .score.popularityScore == null
     )
  ] | length == 0
' "${recommendations_response}" >/dev/null; then
  echo "Assertion failed: Recommendation response has missing required fields" >&2
  cat "${recommendations_response}" >&2
  exit 1
fi

if ! jq -e '
  [range(1; (.data | length)) as $idx
   | select((.data[$idx - 1].score.totalScore // 0) < (.data[$idx].score.totalScore // 0))
  ] | length == 0
' "${recommendations_response}" >/dev/null; then
  echo "Assertion failed: Recommendations should be sorted by totalScore desc" >&2
  cat "${recommendations_response}" >&2
  exit 1
fi

recommendation_count="$(jq '.data | length' "${recommendations_response}")"
top_recommendation_job_id="$(jq -r '.data[0].jobId' "${recommendations_response}")"
top_recommendation_score="$(jq -r '.data[0].score.totalScore' "${recommendations_response}")"

echo
echo "### Missing project checks"
missing_project_skills_response="${tmp_dir}/missing-project-skills.json"
missing_project_skills_status="$(request_get_status "/projects/${MISSING_PROJECT_ID}/skills" "${missing_project_skills_response}")"
assert_missing_project "${missing_project_skills_response}" "${missing_project_skills_status}" "project_skills"

missing_gap_response="${tmp_dir}/missing-gap-analysis.json"
missing_gap_status="$(request_get_status "/gap-analysis/projects/${MISSING_PROJECT_ID}" "${missing_gap_response}" --data-urlencode "limit=${LIMIT}")"
assert_missing_project "${missing_gap_response}" "${missing_gap_status}" "gap_analysis"

echo
echo "### Analytics API Acceptance Summary"
echo "project_skill_count=${skill_count}"
echo "project_experience_tag_count=${experience_tag_count}"
echo "jd_match_count=${jd_match_count}"
echo "top_jd_match_job_id=${top_jd_match_job_id}"
echo "top_jd_match_score=${top_jd_match_score}"
echo "gap_user_skill_count=${gap_user_skill_count}"
echo "gap_match_count=${gap_match_count}"
echo "recommendation_count=${recommendation_count}"
echo "top_recommendation_job_id=${top_recommendation_job_id}"
echo "top_recommendation_score=${top_recommendation_score}"
echo "missing_project_error=USER_PROJECT_NOT_FOUND"

echo
echo "Analytics API acceptance smoke completed."
