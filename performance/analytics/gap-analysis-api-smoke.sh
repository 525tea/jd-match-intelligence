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
IFS=',' read -ra target_role_array <<< "${TARGET_ROLES}"
for role in "${target_role_array[@]}"; do
  if [[ -n "${role}" ]]; then
    target_role_args+=(--data-urlencode "targetRoles=${role}")
  fi
done

echo "BASE_URL=${BASE_URL}"
echo "EMAIL=${EMAIL}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
echo "LIMIT=${LIMIT}"
echo "TARGET_ROLES=${TARGET_ROLES}"
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

echo
echo "### Match Summary"
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
      .matchScore
    ]
  | @tsv
'

echo "Gap analysis API smoke completed."
