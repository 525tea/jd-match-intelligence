#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script."
  exit 1
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
EMAIL="${EMAIL:-gap-smoke@example.com}"
PASSWORD="${PASSWORD:-Jobflow-gap-smoke-123!}"
NAME="${NAME:-Gap Smoke}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
LIMIT="${LIMIT:-10}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK,SOFTWARE_ENGINEER,DEVOPS}"
OUTPUT_DIR="${OUTPUT_DIR:-}"
EXPECT_MISSING_PROJECT_CHECK="${EXPECT_MISSING_PROJECT_CHECK:-true}"
MISSING_PROJECT_ID="${MISSING_PROJECT_ID:-999999999}"

if [[ -z "${USER_PROJECT_ID}" ]]; then
  echo "USER_PROJECT_ID is required."
  echo "Run performance/sql/gap-analysis-smoke-fixture.sql in DB Console first and use its user_project_id."
  exit 1
fi

json_escape() {
  jq -Rn --arg value "$1" '$value'
}

signup() {
  local response_file
  response_file="$(mktemp)"

  local status
  status="$(
    curl --show-error --silent --output "${response_file}" --write-out "%{http_code}" \
      --request POST "${BASE_URL}/auth/signup" \
      --header "Content-Type: application/json" \
      --data @- <<JSON
{
  "email": $(json_escape "${EMAIL}"),
  "password": $(json_escape "${PASSWORD}"),
  "name": $(json_escape "${NAME}")
}
JSON
  )"

  if [[ "${status}" == "201" ]]; then
    echo "Signup completed. email=${EMAIL}"
  elif [[ "${status}" == "409" ]]; then
    echo "Signup skipped because user already exists. email=${EMAIL}"
  else
    echo "Signup failed. status=${status}"
    cat "${response_file}"
    rm -f "${response_file}"
    exit 1
  fi

  rm -f "${response_file}"
}

login() {
  local response
  response="$(
    curl --fail --show-error --silent \
      --request POST "${BASE_URL}/auth/login" \
      --header "Content-Type: application/json" \
      --data @- <<JSON
{
  "email": $(json_escape "${EMAIL}"),
  "password": $(json_escape "${PASSWORD}")
}
JSON
  )"

  local success
  success="$(echo "${response}" | jq -r '.success')"
  if [[ "${success}" != "true" ]]; then
    echo "Login failed."
    echo "${response}" | jq
    exit 1
  fi

  echo "${response}" | jq -r '.data.accessToken'
}

target_role_args=()
target_role_json_array="[]"
IFS=',' read -ra target_role_array <<< "${TARGET_ROLES}"
for role in "${target_role_array[@]}"; do
  if [[ -n "${role}" ]]; then
    target_role_args+=(--data-urlencode "targetRoles=${role}")
    target_role_json_array="$(
      jq --compact-output --arg role "${role}" '. + [$role]' <<< "${target_role_json_array}"
    )"
  fi
done

echo "BASE_URL=${BASE_URL}"
echo "EMAIL=${EMAIL}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
echo "LIMIT=${LIMIT}"
echo "TARGET_ROLES=${TARGET_ROLES}"
echo "EXPECT_MISSING_PROJECT_CHECK=${EXPECT_MISSING_PROJECT_CHECK}"
if [[ "${EXPECT_MISSING_PROJECT_CHECK}" == "true" ]]; then
  echo "MISSING_PROJECT_ID=${MISSING_PROJECT_ID}"
fi
if [[ -n "${OUTPUT_DIR}" ]]; then
  echo "OUTPUT_DIR=${OUTPUT_DIR}"
fi
echo

signup
access_token="$(login)"

echo
echo "### GET /gap-analysis/projects/${USER_PROJECT_ID}"
response="$(
  curl --fail --show-error --silent --get \
    "${BASE_URL}/gap-analysis/projects/${USER_PROJECT_ID}" \
    --header "Authorization: Bearer ${access_token}" \
    --data-urlencode "limit=${LIMIT}" \
    "${target_role_args[@]}"
)"

echo "${response}" | jq

success="$(echo "${response}" | jq -r '.success')"
if [[ "${success}" != "true" ]]; then
  echo "Gap analysis API smoke failed."
  exit 1
fi

job_match_count="$(echo "${response}" | jq '.data.jobMatches | length')"
if [[ "${job_match_count}" -eq 0 ]]; then
  echo "Gap analysis API smoke failed: jobMatches is empty."
  exit 1
fi

missing_detail_count="$(
  echo "${response}" | jq '
    [
      .data.jobMatches[]
      | select(
          (.matchedRequiredSkills | type) != "array"
          or (.missingRequiredSkills | type) != "array"
          or (.matchedPreferredSkills | type) != "array"
          or (.missingPreferredSkills | type) != "array"
        )
    ]
    | length
  '
)"
if [[ "${missing_detail_count}" -ne 0 ]]; then
  echo "Gap analysis API smoke failed: skill gap detail fields are missing."
  exit 1
fi

unexpected_role_count="$(
  echo "${response}" | jq --argjson targetRoles "${target_role_json_array}" '
    [
      .data.jobMatches[]
      | select((.role as $role | $targetRoles | index($role)) == null)
    ]
    | length
  '
)"
if [[ "${unexpected_role_count}" -ne 0 ]]; then
  echo "Gap analysis API smoke failed: response contains role outside TARGET_ROLES."
  echo "Allowed TARGET_ROLES=${TARGET_ROLES}"
  echo "${response}" | jq -r '
    .data.jobMatches[]
    | [.jobId, .title, .role]
    | @tsv
  '
  exit 1
fi

meaningful_gap_detail_count="$(
  echo "${response}" | jq '
    [
      .data.jobMatches[]
      | select(
          ((.matchedRequiredSkills | length)
          + (.missingRequiredSkills | length)
          + (.matchedPreferredSkills | length)
          + (.missingPreferredSkills | length)) > 0
        )
    ]
    | length
  '
)"
if [[ "${meaningful_gap_detail_count}" -eq 0 ]]; then
  echo "Gap analysis API smoke failed: all skill gap detail lists are empty."
  exit 1
fi

echo
echo "### Match Summary"
match_summary="$(
  echo "${response}" | jq -r '
  .data.jobMatches[]
  | [
      .jobId,
      .title,
      .role,
      .requiredSkillCount,
      .matchedRequiredSkillCount,
      .missingRequiredSkillCount,
      .requiredMatchRate,
      .preferredSkillCount,
      .matchedPreferredSkillCount,
      .missingPreferredSkillCount,
      .preferredMatchRate,
      .matchScore,
      (.matchedRequiredSkills | join(", ")),
      (.missingRequiredSkills | join(", ")),
      (.matchedPreferredSkills | join(", ")),
      (.missingPreferredSkills | join(", "))
    ]
  | @tsv
  '
)"

echo "${match_summary}"

if [[ -n "${OUTPUT_DIR}" ]]; then
  mkdir -p "${OUTPUT_DIR}"

  response_path="${OUTPUT_DIR}/gap-analysis-api-response.json"
  summary_path="${OUTPUT_DIR}/gap-analysis-match-summary.tsv"

  echo "${response}" | jq > "${response_path}"
  {
    printf "job_id\ttitle\trole\trequired_skill_count\tmatched_required_skill_count\tmissing_required_skill_count\trequired_match_rate\tpreferred_skill_count\tmatched_preferred_skill_count\tmissing_preferred_skill_count\tpreferred_match_rate\tmatch_score\tmatched_required_skills\tmissing_required_skills\tmatched_preferred_skills\tmissing_preferred_skills\n"
    echo "${match_summary}"
  } > "${summary_path}"

  echo
  echo "Saved response: ${response_path}"
  echo "Saved match summary: ${summary_path}"
fi

if [[ "${EXPECT_MISSING_PROJECT_CHECK}" == "true" ]]; then
  echo
  echo "### GET /gap-analysis/projects/${MISSING_PROJECT_ID} should return USER_PROJECT_NOT_FOUND"

  missing_response_file="$(mktemp)"
  missing_status="$(
    curl --show-error --silent --output "${missing_response_file}" --write-out "%{http_code}" --get \
      "${BASE_URL}/gap-analysis/projects/${MISSING_PROJECT_ID}" \
      --header "Authorization: Bearer ${access_token}" \
      --data-urlencode "limit=5" \
      --data-urlencode "targetRoles=BACKEND"
  )"

  cat "${missing_response_file}" | jq

  missing_success="$(jq -r '.success' "${missing_response_file}")"
  missing_error_code="$(jq -r '.error.code' "${missing_response_file}")"

  if [[ "${missing_status}" != "404" || "${missing_success}" != "false" || "${missing_error_code}" != "USER_PROJECT_NOT_FOUND" ]]; then
    echo "Gap analysis missing project smoke failed."
    echo "Expected status=404 success=false error.code=USER_PROJECT_NOT_FOUND"
    echo "Actual status=${missing_status} success=${missing_success} error.code=${missing_error_code}"
    rm -f "${missing_response_file}"
    exit 1
  fi

  rm -f "${missing_response_file}"
fi

echo "Gap analysis API smoke completed."
