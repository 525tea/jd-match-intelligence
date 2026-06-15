#!/usr/bin/env bash
set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script."
  exit 1
fi

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
OAUTH2_CODE="${OAUTH2_CODE:-}"
EMAIL="${EMAIL:-project-inventory-smoke@example.com}"
PASSWORD="${PASSWORD:-Jobflow-project-inventory-smoke-123!}"
NAME="${NAME:-Project Inventory Smoke}"
OUTPUT_DIR="${OUTPUT_DIR:-}"
EXPECT_MISSING_PROJECT_CHECK="${EXPECT_MISSING_PROJECT_CHECK:-true}"
MISSING_PROJECT_ID="${MISSING_PROJECT_ID:-999999999}"

if [[ -z "${USER_PROJECT_ID}" ]]; then
  echo "USER_PROJECT_ID is required."
  echo "Use a project id owned by the authenticated user."
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

exchange_oauth2_code() {
  local response
  response="$(
    curl --fail --show-error --silent \
      --request POST "${BASE_URL}/auth/oauth2/token" \
      --header "Content-Type: application/json" \
      --data @- <<JSON
{
  "code": $(json_escape "${OAUTH2_CODE}")
}
JSON
  )"

  local success
  success="$(echo "${response}" | jq -r '.success')"
  if [[ "${success}" != "true" ]]; then
    echo "OAuth2 code exchange failed."
    echo "${response}" | jq
    exit 1
  fi

  echo "${response}" | jq -r '.data.accessToken'
}

request_with_token() {
  local title="$1"
  local path="$2"

  echo >&2
  echo "### ${title}" >&2

  curl --fail --show-error --silent --max-time 10 \
    "${BASE_URL}${path}" \
    --header "Authorization: Bearer ${ACCESS_TOKEN}"
}

request_status_with_token() {
  local response_file="$1"
  local path="$2"

  curl --show-error --silent --max-time 10 \
    --output "${response_file}" \
    --write-out "%{http_code}" \
    "${BASE_URL}${path}" \
    --header "Authorization: Bearer ${ACCESS_TOKEN}"
}

validate_inventory_response() {
  local response="$1"
  local label="$2"

  local success
  success="$(echo "${response}" | jq -r '.success')"
  if [[ "${success}" != "true" ]]; then
    echo "${label} smoke failed: success is not true."
    echo "${response}" | jq
    exit 1
  fi

  local data_type
  data_type="$(echo "${response}" | jq -r '.data | type')"
  if [[ "${data_type}" != "array" ]]; then
    echo "${label} smoke failed: data is not an array."
    echo "${response}" | jq
    exit 1
  fi

  local mismatched_project_count
  mismatched_project_count="$(
    echo "${response}" | jq --argjson userProjectId "${USER_PROJECT_ID}" '
      [
        .data[]
        | select(.userProjectId != $userProjectId)
      ]
      | length
    '
  )"
  if [[ "${mismatched_project_count}" -ne 0 ]]; then
    echo "${label} smoke failed: response contains another userProjectId."
    echo "Expected USER_PROJECT_ID=${USER_PROJECT_ID}"
    echo "${response}" | jq
    exit 1
  fi

  local non_latest_count
  non_latest_count="$(
    echo "${response}" | jq '
      [
        .data[]
        | select(.latestAnalysis != true)
      ]
      | length
    '
  )"
  if [[ "${non_latest_count}" -ne 0 ]]; then
    echo "${label} smoke failed: latestAnalysis must be true for all rows."
    echo "${response}" | jq
    exit 1
  fi
}

validate_skill_response() {
  local response="$1"

  validate_inventory_response "${response}" "Project skill inventory"

  local missing_field_count
  missing_field_count="$(
    echo "${response}" | jq '
      [
        .data[]
        | select(
            .analysisId == null
            or .analysisVersion == null
            or .analyzedAt == null
            or .skillId == null
            or .skillName == null
            or .normalizedName == null
            or .category == null
            or .source == null
          )
      ]
      | length
    '
  )"
  if [[ "${missing_field_count}" -ne 0 ]]; then
    echo "Project skill inventory smoke failed: required skill fields are missing."
    echo "${response}" | jq
    exit 1
  fi
}

validate_experience_tag_response() {
  local response="$1"

  validate_inventory_response "${response}" "Project experience tag inventory"

  local missing_field_count
  missing_field_count="$(
    echo "${response}" | jq '
      [
        .data[]
        | select(
            .analysisId == null
            or .analysisVersion == null
            or .analyzedAt == null
            or .tagCode == null
            or .tagName == null
            or .source == null
          )
      ]
      | length
    '
  )"
  if [[ "${missing_field_count}" -ne 0 ]]; then
    echo "Project experience tag inventory smoke failed: required tag fields are missing."
    echo "${response}" | jq
    exit 1
  fi
}

echo "BASE_URL=${BASE_URL}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
echo "EXPECT_MISSING_PROJECT_CHECK=${EXPECT_MISSING_PROJECT_CHECK}"
if [[ -n "${OUTPUT_DIR}" ]]; then
  echo "OUTPUT_DIR=${OUTPUT_DIR}"
fi
echo

if [[ -n "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN is provided. Login will be skipped."
elif [[ -n "${OAUTH2_CODE}" ]]; then
  echo "OAUTH2_CODE is provided. Exchanging it for a JobFlow access token."
  ACCESS_TOKEN="$(exchange_oauth2_code)"
else
  echo "ACCESS_TOKEN and OAUTH2_CODE are empty. The script will signup/login with EMAIL=${EMAIL}."
  signup
  ACCESS_TOKEN="$(login)"
fi

skills_response="$(request_with_token "GET /projects/${USER_PROJECT_ID}/skills" "/projects/${USER_PROJECT_ID}/skills")"
echo "${skills_response}" | jq
validate_skill_response "${skills_response}"

experience_tags_response="$(request_with_token "GET /projects/${USER_PROJECT_ID}/experience-tags" "/projects/${USER_PROJECT_ID}/experience-tags")"
echo "${experience_tags_response}" | jq
validate_experience_tag_response "${experience_tags_response}"

skill_count="$(echo "${skills_response}" | jq '.data | length')"
experience_tag_count="$(echo "${experience_tags_response}" | jq '.data | length')"

echo
echo "Project inventory API smoke counts:"
echo "skill_count=${skill_count}"
echo "experience_tag_count=${experience_tag_count}"

if [[ -n "${OUTPUT_DIR}" ]]; then
  mkdir -p "${OUTPUT_DIR}"
  echo "${skills_response}" | jq > "${OUTPUT_DIR}/project-skills.json"
  echo "${experience_tags_response}" | jq > "${OUTPUT_DIR}/project-experience-tags.json"
  echo "Smoke responses written to OUTPUT_DIR=${OUTPUT_DIR}"
fi

if [[ "${EXPECT_MISSING_PROJECT_CHECK}" == "true" ]]; then
  echo
  echo "### GET /projects/${MISSING_PROJECT_ID}/skills"
  missing_response_file="$(mktemp)"
  missing_status="$(request_status_with_token "${missing_response_file}" "/projects/${MISSING_PROJECT_ID}/skills")"
  cat "${missing_response_file}" | jq

  if [[ "${missing_status}" != "404" ]]; then
    echo "Missing project smoke failed: expected HTTP 404, got ${missing_status}."
    rm -f "${missing_response_file}"
    exit 1
  fi

  missing_error_code="$(jq -r '.error.code' "${missing_response_file}")"
  if [[ "${missing_error_code}" != "USER_PROJECT_NOT_FOUND" ]]; then
    echo "Missing project smoke failed: expected USER_PROJECT_NOT_FOUND, got ${missing_error_code}."
    rm -f "${missing_response_file}"
    exit 1
  fi

  rm -f "${missing_response_file}"
fi

echo
echo "Project inventory API smoke completed."
