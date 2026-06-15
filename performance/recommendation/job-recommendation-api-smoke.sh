#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK}"
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
  echo "Use a project id owned by the authenticated user." >&2
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
  local query="userProjectId=$(urlencode "${USER_PROJECT_ID}")&limit=$(urlencode "${LIMIT}")"

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

  echo "${query}"
}

call_recommendations() {
  local query_string="$1"

  curl --fail --silent --show-error \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    "${BASE_URL}/recommendations/jobs?${query_string}"
}

assert_success_response() {
  local response="$1"
  local success
  local recommendation_count
  local missing_required_fields_count
  local ignored_status_count
  local unsorted_score_count

  success="$(jq -r '.success' <<< "${response}")"
  if [[ "${success}" != "true" ]]; then
    echo "Expected success=true but got:" >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  recommendation_count="$(jq '.data | length' <<< "${response}")"
  if [[ "${recommendation_count}" -lt 1 ]]; then
    echo "Expected at least one recommended job result." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  missing_required_fields_count="$(
    jq '[
      .data[]
      | select(
          .jobId == null
          or .source == null
          or .title == null
          or .companyName == null
          or .role == null
          or .careerLevel == null
          or .status == null
          or .score.totalScore == null
          or .score.skillMatchScore == null
          or .score.freshnessScore == null
          or .score.behaviorScore == null
          or .score.popularityScore == null
        )
    ] | length' <<< "${response}"
  )"
  if [[ "${missing_required_fields_count}" -ne 0 ]]; then
    echo "Recommendation response has missing required fields." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  ignored_status_count="$(jq '[.data[] | select(.userJobStatus == "IGNORED")] | length' <<< "${response}")"
  if [[ "${ignored_status_count}" -ne 0 ]]; then
    echo "Ignored jobs must not be returned in recommendations." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  unsorted_score_count="$(
    jq '[
      range(1; (.data | length)) as $idx
      | select((.data[$idx - 1].score.totalScore // 0) < (.data[$idx].score.totalScore // 0))
    ] | length' <<< "${response}"
  )"
  if [[ "${unsorted_score_count}" -ne 0 ]]; then
    echo "Recommendations must be sorted by totalScore descending." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi
}

assert_missing_project_response() {
  local query_string
  local http_body
  local code
  local response

  query_string="userProjectId=$(urlencode "${MISSING_PROJECT_ID}")&limit=$(urlencode "${LIMIT}")"

  if [[ -n "${TARGET_ROLES}" ]]; then
    IFS=',' read -ra roles <<< "${TARGET_ROLES}"
    for role in "${roles[@]}"; do
      role="${role#"${role%%[![:space:]]*}"}"
      role="${role%"${role##*[![:space:]]}"}"
      if [[ -n "${role}" ]]; then
        query_string="${query_string}&targetRoles=$(urlencode "${role}")"
      fi
    done
  fi

  http_body="$(
    curl --silent --show-error \
      --header "Authorization: Bearer ${ACCESS_TOKEN}" \
      --write-out $'\n%{http_code}' \
      "${BASE_URL}/recommendations/jobs?${query_string}"
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
echo "LIMIT=${LIMIT}"
echo

echo "### GET /recommendations/jobs"
response="$(call_recommendations "${query_string}")"
jq '.' <<< "${response}"
assert_success_response "${response}"

echo
echo "Job recommendation API smoke counts:"
jq -r '
  "recommendation_count=" + ((.data | length) | tostring),
  "top_job_id=" + ((.data[0].jobId // "") | tostring),
  "top_total_score=" + ((.data[0].score.totalScore // "") | tostring),
  "top_skill_match_score=" + ((.data[0].score.skillMatchScore // "") | tostring),
  "top_freshness_score=" + ((.data[0].score.freshnessScore // "") | tostring),
  "top_behavior_score=" + ((.data[0].score.behaviorScore // "") | tostring),
  "top_popularity_score=" + ((.data[0].score.popularityScore // "") | tostring),
  "ignored_status_count=" + (([.data[] | select(.userJobStatus == "IGNORED")] | length) | tostring)
' <<< "${response}"

if [[ -n "${OUTPUT_DIR}" ]]; then
  mkdir -p "${OUTPUT_DIR}"
  response_path="${OUTPUT_DIR}/job-recommendation-api-response.json"
  summary_path="${OUTPUT_DIR}/job-recommendation-api-summary.tsv"

  jq '.' <<< "${response}" > "${response_path}"
  jq -r '
    ["rank","job_id","source","title","company_name","role","career_level","total_score","skill_match_score","freshness_score","behavior_score","popularity_score","user_job_status","matched_required_skill_count","missing_required_skill_count","matched_preferred_skill_count","missing_preferred_skill_count"],
    (.data
      | to_entries[]
      | [
          (.key + 1),
          .value.jobId,
          .value.source,
          .value.title,
          .value.companyName,
          .value.role,
          .value.careerLevel,
          .value.score.totalScore,
          .value.score.skillMatchScore,
          .value.score.freshnessScore,
          .value.score.behaviorScore,
          .value.score.popularityScore,
          (.value.userJobStatus // ""),
          .value.matchedRequiredSkillCount,
          .value.missingRequiredSkillCount,
          .value.matchedPreferredSkillCount,
          .value.missingPreferredSkillCount
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
  echo "### GET /recommendations/jobs with missing project should return USER_PROJECT_NOT_FOUND"
  assert_missing_project_response
fi

echo
echo "Job recommendation API smoke completed."
