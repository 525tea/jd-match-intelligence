#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
EXPECTED_MIN_RESULT_COUNT="${EXPECTED_MIN_RESULT_COUNT:-1}"

echo "BASE_URL=${BASE_URL}"
echo "EXPECTED_MIN_RESULT_COUNT=${EXPECTED_MIN_RESULT_COUNT}"

jobs_response="$(curl --fail --silent --show-error "${BASE_URL}/jobs?page=0&size=20")"
jobs_count="$(printf '%s' "${jobs_response}" | jq -r 'if (.data | type) == "array" then (.data | length) else (.data.totalElements // .data.total // .data.totalCount // 0) end')"

if [[ -z "${jobs_count}" || "${jobs_count}" == "null" || "${jobs_count}" == "0" ]]; then
  echo "Expected /jobs to return at least one result." >&2
  printf '%s\n' "${jobs_response}" >&2
  exit 1
fi

echo "jobs_result_count=${jobs_count}"

search_response="$(curl --fail --silent --show-error "${BASE_URL}/jobs/search?keyword=performance&limit=5")"
search_success="$(printf '%s' "${search_response}" | jq -r '.success')"
search_count="$(printf '%s' "${search_response}" | jq -r '.data | length')"
search_perf_count="$(printf '%s' "${search_response}" | jq -r '[.data[]? | select((.externalId // "") | startswith("perf-job-"))] | length')"

echo "jobs_search_success=${search_success}"
echo "jobs_search_count=${search_count}"
echo "jobs_search_perf_external_id_count=${search_perf_count}"

if [[ "${search_success}" != "true" ]]; then
  echo "Expected /jobs/search to return success=true." >&2
  printf '%s\n' "${search_response}" >&2
  exit 1
fi

if (( search_perf_count < EXPECTED_MIN_RESULT_COUNT )); then
  echo "Expected /jobs/search to return at least ${EXPECTED_MIN_RESULT_COUNT} performance fixture rows." >&2
  printf '%s\n' "${search_response}" >&2
  exit 1
fi

echo "Performance profile smoke completed."
