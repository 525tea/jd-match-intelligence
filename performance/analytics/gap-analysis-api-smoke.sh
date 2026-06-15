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
if [[ ! "${LIMIT}" =~ ^[0-9]+$ || "${LIMIT}" -lt 1 || "${LIMIT}" -gt 50 ]]; then
  echo "LIMIT must be an integer between 1 and 50."
  echo "Actual LIMIT=${LIMIT}"
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
if [[ "${job_match_count}" -gt "${LIMIT}" ]]; then
  echo "Gap analysis API smoke failed: jobMatches exceeds LIMIT."
  echo "LIMIT=${LIMIT}, job_match_count=${job_match_count}"
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

invalid_match_rate_count="$(
  echo "${response}" | jq '
    [
      .data.jobMatches[]
      | select(
          (.requiredSkillCount == 0 and .requiredMatchRate != null)
          or (.requiredSkillCount > 0 and .requiredMatchRate == null)
          or (.preferredSkillCount == 0 and .preferredMatchRate != null)
          or (.preferredSkillCount > 0 and .preferredMatchRate == null)
        )
    ]
    | length
  '
)"
if [[ "${invalid_match_rate_count}" -ne 0 ]]; then
  echo "Gap analysis API smoke failed: match rate nullability does not match skill bucket counts."
  echo "${response}" | jq -r '
    .data.jobMatches[]
    | select(
        (.requiredSkillCount == 0 and .requiredMatchRate != null)
        or (.requiredSkillCount > 0 and .requiredMatchRate == null)
        or (.preferredSkillCount == 0 and .preferredMatchRate != null)
        or (.preferredSkillCount > 0 and .preferredMatchRate == null)
      )
    | [
        .jobId,
        .title,
        .role,
        .requiredSkillCount,
        (.requiredMatchRate // "null"),
        .preferredSkillCount,
        (.preferredMatchRate // "null")
      ]
    | @tsv
  '
  exit 1
fi

missing_evidence_field_count="$(
  echo "${response}" | jq '
    [
      .data.jobMatches[]
      | select(
          (.evidence | type) != "object"
          or (.evidence.addedJobs | type) != "number"
          or (.evidence.cooccurrences | type) != "array"
          or (.evidence.relatedTags | type) != "array"
          or (.evidence.learningConnections | type) != "array"
        )
    ]
    | length
  '
)"
if [[ "${missing_evidence_field_count}" -ne 0 ]]; then
  echo "Gap analysis API smoke failed: evidence fields are missing or malformed."
  exit 1
fi

meaningful_evidence_count="$(
  echo "${response}" | jq '
    [
      .data.jobMatches[]
      | select(
          (.evidence.addedJobs > 0)
          or ((.evidence.cooccurrences | length) > 0)
          or ((.evidence.relatedTags | length) > 0)
          or ((.evidence.learningConnections | length) > 0)
        )
    ]
    | length
  '
)"
if [[ "${meaningful_evidence_count}" -eq 0 ]]; then
  echo "Gap analysis API smoke failed: every evidence object is empty."
  exit 1
fi

evidence_added_jobs_sum="$(echo "${response}" | jq '[.data.jobMatches[].evidence.addedJobs] | add // 0')"
cooccurrence_evidence_count="$(echo "${response}" | jq '[.data.jobMatches[].evidence.cooccurrences | length] | add // 0')"
related_tag_evidence_count="$(echo "${response}" | jq '[.data.jobMatches[].evidence.relatedTags | length] | add // 0')"
learning_connection_count="$(echo "${response}" | jq '[.data.jobMatches[].evidence.learningConnections | length] | add // 0')"

echo
echo "### Evidence Summary"
echo "meaningful_evidence_count=${meaningful_evidence_count}"
echo "evidence_added_jobs_sum=${evidence_added_jobs_sum}"
echo "cooccurrence_evidence_count=${cooccurrence_evidence_count}"
echo "related_tag_evidence_count=${related_tag_evidence_count}"
echo "learning_connection_count=${learning_connection_count}"

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
      (.missingPreferredSkills | join(", ")),
      .evidence.addedJobs,
      (.evidence.cooccurrences | length),
      (.evidence.relatedTags | length),
      (.evidence.learningConnections | length)
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
    printf "job_id\ttitle\trole\trequired_skill_count\tmatched_required_skill_count\tmissing_required_skill_count\trequired_match_rate\tpreferred_skill_count\tmatched_preferred_skill_count\tmissing_preferred_skill_count\tpreferred_match_rate\tmatch_score\tmatched_required_skills\tmissing_required_skills\tmatched_preferred_skills\tmissing_preferred_skills\tevidence_added_jobs\tcooccurrence_evidence_count\trelated_tag_evidence_count\tlearning_connection_count\n"
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
