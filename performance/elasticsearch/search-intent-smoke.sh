#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081/api}"
LIMIT="${LIMIT:-5}"
MIN_EXPECTED_ROLE_HITS="${MIN_EXPECTED_ROLE_HITS:-1}"

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

assert_greater_or_equal() {
  local actual="$1"
  local expected="$2"
  local message="$3"

  if (( actual < expected )); then
    echo "Assertion failed: ${message}" >&2
    echo "Expected at least: ${expected}" >&2
    echo "Actual: ${actual}" >&2
    exit 1
  fi
}

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script." >&2
  exit 1
fi

echo "BASE_URL=${BASE_URL}"
echo "LIMIT=${LIMIT}"
echo "MIN_EXPECTED_ROLE_HITS=${MIN_EXPECTED_ROLE_HITS}"
echo

# query|expected_roles_csv
QUERIES=(
  "backend junior seoul|BACKEND"
  "프론트엔드 React|FRONTEND"
  "데이터 엔지니어|DATA_ENGINEER,DATA_SCIENTIST,DATA_ANALYST"
)

total_query_count=0
passed_query_count=0

for query_config in "${QUERIES[@]}"; do
  IFS='|' read -r keyword expected_roles_csv <<< "${query_config}"
  total_query_count=$((total_query_count + 1))

  echo "### Search intent: ${keyword}"

  response="$(
    curl -sS -G "${BASE_URL}/jobs/search" \
      --data-urlencode "keyword=${keyword}" \
      --data-urlencode "limit=${LIMIT}"
  )"

  success="$(echo "${response}" | jq -r '.success')"
  assert_equals "${success}" "true" "GET /jobs/search should return success=true for '${keyword}'"

  row_count="$(echo "${response}" | jq '.data | length')"
  assert_greater_or_equal "${row_count}" 1 "GET /jobs/search should return at least one result for '${keyword}'"

  expected_role_hits="$(
    echo "${response}" | jq --arg expected_roles_csv "${expected_roles_csv}" '
      ($expected_roles_csv | split(",")) as $expectedRoles
      | [.data[] | select(.role as $role | $expectedRoles | index($role))] | length
    '
  )"

  echo "${response}" | jq -r '
    .data[]
    | [
        "rank=\(.id)",
        "role=\(.role)",
        "career=\(.careerLevel)",
        "region=\(.locationRegion // "")",
        "title=\(.title)",
        "score=\(.score // "")"
      ]
    | @tsv
  '
  echo "expected_roles=${expected_roles_csv}"
  echo "expected_role_hits=${expected_role_hits}"
  echo

  assert_greater_or_equal \
    "${expected_role_hits}" \
    "${MIN_EXPECTED_ROLE_HITS}" \
    "Search intent '${keyword}' should include expected role in top ${LIMIT}"

  passed_query_count=$((passed_query_count + 1))
done

echo "### Search Intent Smoke Summary"
echo "total_query_count=${total_query_count}"
echo "passed_query_count=${passed_query_count}"
echo "limit=${LIMIT}"
echo
echo "Search intent smoke completed."
