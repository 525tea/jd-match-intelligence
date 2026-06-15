#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK}"
TARGET_CAREER_LEVEL="${TARGET_CAREER_LEVEL:-}"
LIMIT="${LIMIT:-10}"
EXPECT_MISSING_PROJECT_CHECK="${EXPECT_MISSING_PROJECT_CHECK:-true}"
MISSING_PROJECT_ID="${MISSING_PROJECT_ID:-999999999}"
OUTPUT_DIR="${OUTPUT_DIR:-}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

if [[ -z "${USER_PROJECT_ID}" ]]; then
  echo "USER_PROJECT_ID is required." >&2
  echo "Run performance/sql/jd-match-smoke-check.sql in DB Console first and use a project with project_skill_count > 0." >&2
  exit 1
fi

if [[ -z "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN is required." >&2
  echo "Login through JobFlow first, then pass the JobFlow JWT as ACCESS_TOKEN." >&2
  exit 1
fi

urlencode() {
  jq -nr --arg value "$1" '$value|@uri'
}

build_query_string() {
  local query="limit=$(urlencode "${LIMIT}")"

  if [[ -n "${TARGET_ROLES}" ]]; then
    IFS=',' read -ra roles <<< "${TARGET_ROLES}"
    for role in "${roles[@]}"; do
      role="${role#"${role%%[![:space:]]*}"}"
      role="${role%"${role##*[![:space:]]}"}"
      if [[ -n "${role}" ]]; then
        query="${query}&targetRoles=$(urlencode "${role}")"
      fi
    done
  fi

  if [[ -n "${TARGET_CAREER_LEVEL}" ]]; then
    query="${query}&targetCareerLevel=$(urlencode "${TARGET_CAREER_LEVEL}")"
  fi

  echo "${query}"
}

call_job_matches() {
  local project_id="$1"
  local query_string="$2"

  curl --fail --silent --show-error \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/projects/${project_id}/job-matches?${query_string}"
}

assert_success_response() {
  local response="$1"
  local success
  local match_count
  local missing_score_count
  local missing_required_fields_count

  success="$(jq -r '.success' <<< "${response}")"
  if [[ "${success}" != "true" ]]; then
    echo "Expected success=true but got:" >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  match_count="$(jq '.data | length' <<< "${response}")"
  if [[ "${match_count}" -lt 1 ]]; then
    echo "Expected at least one JD match result." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  missing_score_count="$(jq '[.data[] | select(.score.totalScore == null)] | length' <<< "${response}")"
  if [[ "${missing_score_count}" -ne 0 ]]; then
    echo "Every JD match result must include score.totalScore." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  missing_required_fields_count="$(
    jq '[
      .data[]
      | select(
          .jobId == null
          or .title == null
          or .companyName == null
          or .role == null
          or .score.requiredSkillScore == null
          or .score.preferredSkillScore == null
          or .score.experienceTagScore == null
          or .score.careerLevelScore == null
          or .score.confidenceScore == null
        )
    ] | length' <<< "${response}"
  )"
  if [[ "${missing_required_fields_count}" -ne 0 ]]; then
    echo "JD match response has missing required fields." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi
}

assert_missing_project_response() {
  local query_string="$1"
  local http_body
  local code

  http_body="$(
    curl --silent --show-error \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --write-out $'\n%{http_code}' \
      "${BASE_URL}/projects/${MISSING_PROJECT_ID}/job-matches?${query_string}"
  )"

  code="$(tail -n 1 <<< "${http_body}")"
  response="$(sed '$d' <<< "${http_body}")"

  if [[ "${code}" != "404" ]]; then
    echo "Expected missing project check to return HTTP 404 but got ${code}." >&2
    jq '.' <<< "${response}" >&2 || echo "${response}" >&2
    exit 1
  fi

  if [[ "$(jq -r '.error.code' <<< "${response}")" != "USER_PROJECT_NOT_FOUND" ]]; then
    echo "Expected USER_PROJECT_NOT_FOUND for missing project check." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  jq '.' <<< "${response}"
}

query_string="$(build_query_string)"

echo "BASE_URL=${BASE_URL}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
echo "TARGET_ROLES=${TARGET_ROLES}"
echo "TARGET_CAREER_LEVEL=${TARGET_CAREER_LEVEL}"
echo "LIMIT=${LIMIT}"
echo

echo "### GET /projects/${USER_PROJECT_ID}/job-matches"
response="$(call_job_matches "${USER_PROJECT_ID}" "${query_string}")"
jq '.' <<< "${response}"
assert_success_response "${response}"

echo
echo "JD match API smoke counts:"
jq -r '
  "match_count=" + ((.data | length) | tostring),
  "top_job_id=" + ((.data[0].jobId // "") | tostring),
  "top_total_score=" + ((.data[0].score.totalScore // "") | tostring),
  "top_required_skill_score=" + ((.data[0].score.requiredSkillScore // "") | tostring),
  "top_preferred_skill_score=" + ((.data[0].score.preferredSkillScore // "") | tostring),
  "top_experience_tag_score=" + ((.data[0].score.experienceTagScore // "") | tostring),
  "top_career_level_score=" + ((.data[0].score.careerLevelScore // "") | tostring),
  "top_confidence_score=" + ((.data[0].score.confidenceScore // "") | tostring)
' <<< "${response}"

if [[ -n "${OUTPUT_DIR}" ]]; then
  mkdir -p "${OUTPUT_DIR}"
  response_path="${OUTPUT_DIR}/jd-match-api-response.json"
  summary_path="${OUTPUT_DIR}/jd-match-api-summary.tsv"

  jq '.' <<< "${response}" > "${response_path}"
  jq -r '
    ["rank","job_id","title","company_name","role","career_level","total_score","required_skill_score","preferred_skill_score","experience_tag_score","career_level_score","confidence_score","matched_required_skill_count","missing_required_skill_count","matched_preferred_skill_count","missing_preferred_skill_count","matched_experience_tag_count","missing_experience_tag_count"],
    (.data
      | to_entries[]
      | [
          (.key + 1),
          .value.jobId,
          .value.title,
          .value.companyName,
          .value.role,
          .value.careerLevel,
          .value.score.totalScore,
          .value.score.requiredSkillScore,
          .value.score.preferredSkillScore,
          .value.score.experienceTagScore,
          .value.score.careerLevelScore,
          .value.score.confidenceScore,
          .value.matchedRequiredSkillCount,
          .value.missingRequiredSkillCount,
          .value.matchedPreferredSkillCount,
          .value.missingPreferredSkillCount,
          .value.matchedExperienceTagCount,
          .value.missingExperienceTagCount
        ])
    | @tsv
  ' <<< "${response}" > "${summary_path}"

  echo
  echo "Saved smoke artifacts:"
  echo "${response_path}"
  echo "${summary_path}"
fi

if [[ "${EXPECT_MISSING_PROJECT_CHECK}" == "true" ]]; then
  echo
  echo "### GET /projects/${MISSING_PROJECT_ID}/job-matches should return USER_PROJECT_NOT_FOUND"
  assert_missing_project_response "${query_string}"
fi

echo
echo "JD match API smoke completed."

