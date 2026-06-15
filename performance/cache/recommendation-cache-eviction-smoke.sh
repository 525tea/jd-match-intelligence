#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK}"
LIMIT="${LIMIT:-10}"
USER_JOB_ACTION="${USER_JOB_ACTION:-view}"
CLEAR_CACHE="${CLEAR_CACHE:-true}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required." >&2
  exit 1
fi

if [[ -z "${USER_PROJECT_ID}" ]]; then
  echo "USER_PROJECT_ID is required." >&2
  echo "Use a project id owned by the authenticated user." >&2
  exit 1
fi

if [[ -z "${ACCESS_TOKEN}" ]]; then
  echo "ACCESS_TOKEN is required." >&2
  echo "ACCESS_TOKEN must be a JobFlow JWT." >&2
  echo "Do not pass GitHub Client Secret, PAT, GitHub Actions token, or OAuth provider token here." >&2
  exit 1
fi

case "${USER_JOB_ACTION}" in
  view|save|ignore)
    ;;
  *)
    echo "Unsupported USER_JOB_ACTION=${USER_JOB_ACTION}." >&2
    echo "Supported actions: view, save, ignore" >&2
    exit 1
    ;;
esac

urlencode() {
  jq -nr --arg value "$1" '$value|@uri'
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  echo "${value}"
}

redis_cli() {
  docker compose exec -T redis redis-cli "$@"
}

redis_scan_count() {
  local pattern="$1"
  redis_cli --scan --pattern "${pattern}" | wc -l | tr -d '[:space:]'
}

redis_delete_pattern() {
  local pattern="$1"
  local deleted=0

  while IFS= read -r key; do
    if [[ -n "${key}" ]]; then
      redis_cli DEL "${key}" >/dev/null
      deleted=$((deleted + 1))
    fi
  done < <(redis_cli --scan --pattern "${pattern}")

  echo "${deleted}"
}

build_roles_query() {
  local query=""
  if [[ -n "${TARGET_ROLES}" ]]; then
    IFS=',' read -ra roles <<< "${TARGET_ROLES}"
    for role in "${roles[@]}"; do
      role="$(trim "${role}")"
      if [[ -n "${role}" ]]; then
        query="${query}&targetRoles=$(urlencode "${role}")"
      fi
    done
  fi
  echo "${query}"
}

recommendation_query_string() {
  echo "userProjectId=$(urlencode "${USER_PROJECT_ID}")&limit=$(urlencode "${LIMIT}")$(build_roles_query)"
}

get_recommendations() {
  local response_file="$1"
  local query_string
  query_string="$(recommendation_query_string)"

  curl --fail --silent --show-error \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --output "${response_file}" \
    "${BASE_URL}/recommendations/jobs?${query_string}"
}

post_user_job_action() {
  local job_id="$1"

  curl --fail --silent --show-error \
    --request POST \
    --header "Authorization: Bearer ${ACCESS_TOKEN}" \
    --output /dev/null \
    "${BASE_URL}/user/jobs/${job_id}/${USER_JOB_ACTION}"
}

cache_pattern="jobRecommendation::*"

echo "BASE_URL=${BASE_URL}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
echo "TARGET_ROLES=${TARGET_ROLES}"
echo "LIMIT=${LIMIT}"
echo "USER_JOB_ACTION=${USER_JOB_ACTION}"
echo "CACHE_PATTERN=${cache_pattern}"
echo "CLEAR_CACHE=${CLEAR_CACHE}"
echo

if [[ "${CLEAR_CACHE}" == "true" ]]; then
  deleted_count="$(redis_delete_pattern "${cache_pattern}")"
  echo "Deleted existing recommendation cache keys: ${deleted_count}"
  echo
fi

before_key_count="$(redis_scan_count "${cache_pattern}")"

first_response_file="$(mktemp)"
second_response_file="$(mktemp)"

get_recommendations "${first_response_file}"
first_success="$(jq -r '.success' "${first_response_file}")"
if [[ "${first_success}" != "true" ]]; then
  echo "First recommendation request failed." >&2
  jq '.' "${first_response_file}" >&2
  exit 1
fi

first_count="$(jq '.data | length' "${first_response_file}")"
if [[ "${first_count}" -lt 1 ]]; then
  echo "Expected at least one recommendation result." >&2
  jq '.' "${first_response_file}" >&2
  exit 1
fi

after_first_key_count="$(redis_scan_count "${cache_pattern}")"
if [[ "${after_first_key_count}" -lt 1 ]]; then
  echo "Expected recommendation cache key after first request." >&2
  exit 1
fi

get_recommendations "${second_response_file}"
second_success="$(jq -r '.success' "${second_response_file}")"
if [[ "${second_success}" != "true" ]]; then
  echo "Second recommendation request failed." >&2
  jq '.' "${second_response_file}" >&2
  exit 1
fi

after_second_key_count="$(redis_scan_count "${cache_pattern}")"
top_job_id="$(jq -r '.data[0].jobId' "${second_response_file}")"

post_user_job_action "${top_job_id}"

after_event_key_count="$(redis_scan_count "${cache_pattern}")"
if [[ "${after_event_key_count}" -ne 0 ]]; then
  echo "Expected recommendation cache keys to be evicted after user job action." >&2
  echo "remaining_key_count=${after_event_key_count}" >&2
  redis_cli --scan --pattern "${cache_pattern}" >&2
  exit 1
fi

echo "### Recommendation Cache Eviction Smoke Summary"
echo "before_key_count=${before_key_count}"
echo "after_first_key_count=${after_first_key_count}"
echo "after_second_key_count=${after_second_key_count}"
echo "after_event_key_count=${after_event_key_count}"
echo "recommendation_count=${first_count}"
echo "top_job_id=${top_job_id}"
echo "user_job_action=${USER_JOB_ACTION}"

rm -f "${first_response_file}" "${second_response_file}"

echo
echo "Recommendation cache eviction smoke completed."
