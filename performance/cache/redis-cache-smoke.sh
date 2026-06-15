#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MODE="${MODE:-gap-analysis}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
EMAIL="${EMAIL:-}"
PASSWORD="${PASSWORD:-}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK}"
TARGET_CAREER_LEVEL="${TARGET_CAREER_LEVEL:-}"
MONTH="${MONTH:-2026-06-01}"
LIMIT="${LIMIT:-10}"
CLEAR_CACHE="${CLEAR_CACHE:-false}"
OUTPUT_DIR="${OUTPUT_DIR:-}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required." >&2
  exit 1
fi

urlencode() {
  jq -nr --arg value "$1" '$value|@uri'
}

json_escape() {
  jq -Rn --arg value "$1" '$value'
}

login_with_email_password() {
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
  success="$(jq -r '.success' <<< "${response}")"
  if [[ "${success}" != "true" ]]; then
    echo "Login failed." >&2
    jq '.' <<< "${response}" >&2
    exit 1
  fi

  jq -r '.data.accessToken' <<< "${response}"
}

redis_cli() {
  docker compose exec -T redis redis-cli "$@"
}

redis_stat() {
  local name="$1"
  redis_cli INFO stats \
    | tr -d '\r' \
    | awk -F: -v key="${name}" '$1 == key { print $2 }'
}

redis_scan_count() {
  local pattern="$1"
  local count
  count="$(redis_cli --scan --pattern "${pattern}" | wc -l | tr -d '[:space:]')"
  echo "${count}"
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

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  echo "${value}"
}

build_repeated_roles_query() {
  local query=""
  if [[ -n "${TARGET_ROLES}" ]]; then
    IFS=',' read -ra roles <<< "${TARGET_ROLES}"
    for role in "${roles[@]}"; do
      role="$(trim "${role}")"
      if [[ -n "${role}" ]]; then
        if [[ -n "${query}" ]]; then
          query="${query}&"
        fi
        query="${query}targetRoles=$(urlencode "${role}")"
      fi
    done
  fi
  echo "${query}"
}

cache_pattern_for_mode() {
  case "${MODE}" in
    trend-skills)
      echo "trendSkills::*"
      ;;
    gap-analysis)
      echo "gapAnalysis::*"
      ;;
    jd-match)
      echo "jdMatch::*"
      ;;
    job-recommendation)
      echo "jobRecommendation::*"
      ;;
    project-skills)
      echo "projectSkillInventory::*"
      ;;
    project-experience-tags)
      echo "projectExperienceTagInventory::*"
      ;;
    *)
      echo "Unsupported MODE=${MODE}" >&2
      echo "Supported modes: trend-skills, gap-analysis, jd-match, job-recommendation, project-skills, project-experience-tags" >&2
      exit 1
      ;;
  esac
}

request_path_for_mode() {
  local roles_query
  roles_query="$(build_repeated_roles_query)"

  case "${MODE}" in
    trend-skills)
      echo "/trends/skills?month=$(urlencode "${MONTH}")&limit=$(urlencode "${LIMIT}")"
      ;;
    gap-analysis)
      if [[ -z "${USER_PROJECT_ID}" ]]; then
        echo "USER_PROJECT_ID is required for MODE=gap-analysis." >&2
        exit 1
      fi

      local query="limit=$(urlencode "${LIMIT}")"
      if [[ -n "${roles_query}" ]]; then
        query="${query}&${roles_query}"
      fi
      echo "/gap-analysis/projects/${USER_PROJECT_ID}?${query}"
      ;;
    jd-match)
      if [[ -z "${USER_PROJECT_ID}" ]]; then
        echo "USER_PROJECT_ID is required for MODE=jd-match." >&2
        exit 1
      fi

      local query="limit=$(urlencode "${LIMIT}")"
      if [[ -n "${roles_query}" ]]; then
        query="${query}&${roles_query}"
      fi
      if [[ -n "${TARGET_CAREER_LEVEL}" ]]; then
        query="${query}&targetCareerLevel=$(urlencode "${TARGET_CAREER_LEVEL}")"
      fi
      echo "/projects/${USER_PROJECT_ID}/job-matches?${query}"
      ;;
    job-recommendation)
      if [[ -z "${USER_PROJECT_ID}" ]]; then
        echo "USER_PROJECT_ID is required for MODE=job-recommendation." >&2
        exit 1
      fi

      local query="userProjectId=$(urlencode "${USER_PROJECT_ID}")&limit=$(urlencode "${LIMIT}")"
      if [[ -n "${roles_query}" ]]; then
        query="${query}&${roles_query}"
      fi
      echo "/recommendations/jobs?${query}"
      ;;
    project-skills)
      if [[ -z "${USER_PROJECT_ID}" ]]; then
        echo "USER_PROJECT_ID is required for MODE=project-skills." >&2
        exit 1
      fi
      echo "/projects/${USER_PROJECT_ID}/skills"
      ;;
    project-experience-tags)
      if [[ -z "${USER_PROJECT_ID}" ]]; then
        echo "USER_PROJECT_ID is required for MODE=project-experience-tags." >&2
        exit 1
      fi
      echo "/projects/${USER_PROJECT_ID}/experience-tags"
      ;;
  esac
}

requires_auth() {
  case "${MODE}" in
    trend-skills)
      return 1
      ;;
    *)
      return 0
      ;;
  esac
}

ensure_access_token() {
  if ! requires_auth; then
    return
  fi

  if [[ -n "${ACCESS_TOKEN}" ]]; then
    return
  fi

  if [[ -z "${EMAIL}" || -z "${PASSWORD}" ]]; then
    echo "ACCESS_TOKEN is required for MODE=${MODE} unless EMAIL/PASSWORD are provided." >&2
    echo "ACCESS_TOKEN must be a JobFlow JWT." >&2
    echo "Do not pass GitHub Client Secret, PAT, GitHub Actions token, or OAuth provider token here." >&2
    exit 1
  fi

  ACCESS_TOKEN="$(login_with_email_password)"
}

request_once() {
  local label="$1"
  local path="$2"
  local response_file="$3"
  local time_file="$4"

  local http_code
  if requires_auth; then
    http_code="$(
      curl --silent --show-error \
        --output "${response_file}" \
        --write-out "%{http_code} %{time_total}" \
        --header "Authorization: Bearer ${ACCESS_TOKEN}" \
        "${BASE_URL}${path}"
    )"
  else
    http_code="$(
      curl --silent --show-error \
        --output "${response_file}" \
        --write-out "%{http_code} %{time_total}" \
        "${BASE_URL}${path}"
    )"
  fi

  local status
  local time_total
  status="$(awk '{ print $1 }' <<< "${http_code}")"
  time_total="$(awk '{ print $2 }' <<< "${http_code}")"
  echo "${time_total}" > "${time_file}"

  if [[ "${status}" != "200" ]]; then
    echo "${label} request failed. status=${status}" >&2
    cat "${response_file}" >&2
    exit 1
  fi

  local success
  success="$(jq -r '.success' "${response_file}")"
  if [[ "${success}" != "true" ]]; then
    echo "${label} request failed. success is not true." >&2
    jq '.' "${response_file}" >&2
    exit 1
  fi
}

cache_pattern="$(cache_pattern_for_mode)"
request_path="$(request_path_for_mode)"
ensure_access_token

echo "BASE_URL=${BASE_URL}"
echo "MODE=${MODE}"
echo "USER_PROJECT_ID=${USER_PROJECT_ID}"
if requires_auth; then
  echo "AUTH=JobFlow JWT"
else
  echo "AUTH=none"
fi
echo "TARGET_ROLES=${TARGET_ROLES}"
echo "TARGET_CAREER_LEVEL=${TARGET_CAREER_LEVEL}"
echo "MONTH=${MONTH}"
echo "LIMIT=${LIMIT}"
echo "CLEAR_CACHE=${CLEAR_CACHE}"
echo "CACHE_PATTERN=${cache_pattern}"
echo

if [[ "${CLEAR_CACHE}" == "true" ]]; then
  deleted_count="$(redis_delete_pattern "${cache_pattern}")"
  echo "Deleted existing cache keys: ${deleted_count}"
  echo
fi

before_hits="$(redis_stat keyspace_hits)"
before_misses="$(redis_stat keyspace_misses)"
before_key_count="$(redis_scan_count "${cache_pattern}")"

first_response_file="$(mktemp)"
second_response_file="$(mktemp)"
first_time_file="$(mktemp)"
second_time_file="$(mktemp)"

request_once "first" "${request_path}" "${first_response_file}" "${first_time_file}"
mid_hits="$(redis_stat keyspace_hits)"
mid_misses="$(redis_stat keyspace_misses)"
mid_key_count="$(redis_scan_count "${cache_pattern}")"

request_once "second" "${request_path}" "${second_response_file}" "${second_time_file}"
after_hits="$(redis_stat keyspace_hits)"
after_misses="$(redis_stat keyspace_misses)"
after_key_count="$(redis_scan_count "${cache_pattern}")"

first_time="$(cat "${first_time_file}")"
second_time="$(cat "${second_time_file}")"

first_data_count="$(jq '.data | if type == "array" then length else 1 end' "${first_response_file}")"
second_data_count="$(jq '.data | if type == "array" then length else 1 end' "${second_response_file}")"

echo "### Redis Cache Smoke Summary"
echo "first_time_total=${first_time}"
echo "second_time_total=${second_time}"
echo "first_data_count=${first_data_count}"
echo "second_data_count=${second_data_count}"
echo "keyspace_hits_before=${before_hits}"
echo "keyspace_hits_after_first=${mid_hits}"
echo "keyspace_hits_after_second=${after_hits}"
echo "keyspace_misses_before=${before_misses}"
echo "keyspace_misses_after_first=${mid_misses}"
echo "keyspace_misses_after_second=${after_misses}"
echo "cache_key_count_before=${before_key_count}"
echo "cache_key_count_after_first=${mid_key_count}"
echo "cache_key_count_after_second=${after_key_count}"
echo "hit_delta_total=$((after_hits - before_hits))"
echo "miss_delta_total=$((after_misses - before_misses))"

if [[ "${after_key_count}" -lt 1 ]]; then
  echo "Expected at least one Redis cache key for pattern ${cache_pattern}, but found ${after_key_count}." >&2
  exit 1
fi

if [[ "${second_data_count}" != "${first_data_count}" ]]; then
  echo "First and second response data count should match." >&2
  exit 1
fi

if [[ -n "${OUTPUT_DIR}" ]]; then
  mkdir -p "${OUTPUT_DIR}"

  cp "${first_response_file}" "${OUTPUT_DIR}/${MODE}-first-response.json"
  cp "${second_response_file}" "${OUTPUT_DIR}/${MODE}-second-response.json"

  {
    echo -e "metric\tvalue"
    echo -e "mode\t${MODE}"
    echo -e "cache_pattern\t${cache_pattern}"
    echo -e "first_time_total\t${first_time}"
    echo -e "second_time_total\t${second_time}"
    echo -e "first_data_count\t${first_data_count}"
    echo -e "second_data_count\t${second_data_count}"
    echo -e "hit_delta_total\t$((after_hits - before_hits))"
    echo -e "miss_delta_total\t$((after_misses - before_misses))"
    echo -e "cache_key_count_after_second\t${after_key_count}"
  } > "${OUTPUT_DIR}/${MODE}-cache-summary.tsv"

  echo
  echo "Saved smoke artifacts:"
  echo "${OUTPUT_DIR}/${MODE}-first-response.json"
  echo "${OUTPUT_DIR}/${MODE}-second-response.json"
  echo "${OUTPUT_DIR}/${MODE}-cache-summary.tsv"
fi

rm -f "${first_response_file}" "${second_response_file}" "${first_time_file}" "${second_time_file}"

echo
echo "Redis cache smoke completed."
