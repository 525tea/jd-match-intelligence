#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8081/api}"
LIMIT="${LIMIT:-5}"
MIN_EXPECTED_ROLE_HITS="${MIN_EXPECTED_ROLE_HITS:-1}"
MIN_EXPECTED_CAREER_HITS="${MIN_EXPECTED_CAREER_HITS:-1}"
MIN_EXPECTED_REGION_HITS="${MIN_EXPECTED_REGION_HITS:-1}"

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
echo "MIN_EXPECTED_CAREER_HITS=${MIN_EXPECTED_CAREER_HITS}"
echo "MIN_EXPECTED_REGION_HITS=${MIN_EXPECTED_REGION_HITS}"
echo

# query|expected_roles_csv|expected_careers_csv|expected_regions_csv
QUERIES=(
  "backend junior seoul|BACKEND|JUNIOR|Seoul"
  "프론트엔드 React|FRONTEND||"
  "데이터 엔지니어|DATA_ENGINEER,DATA_SCIENTIST,DATA_ANALYST||"
)

total_query_count=0
passed_query_count=0

for query_config in "${QUERIES[@]}"; do
  IFS='|' read -r keyword expected_roles_csv expected_careers_csv expected_regions_csv <<< "${query_config}"
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
  expected_career_hits=0
  expected_region_hits=0

  if [[ -n "${expected_careers_csv}" ]]; then
    expected_career_hits="$(
      echo "${response}" | jq --arg expected_careers_csv "${expected_careers_csv}" '
        ($expected_careers_csv | split(",")) as $expectedCareers
        | [.data[] | select(.careerLevel as $career | $expectedCareers | index($career))] | length
      '
    )"
  fi

  if [[ -n "${expected_regions_csv}" ]]; then
    expected_region_hits="$(
      echo "${response}" | jq --arg expected_regions_csv "${expected_regions_csv}" '
        ($expected_regions_csv | split(",")) as $expectedRegions
        | [.data[] | select(.locationRegion as $region | $expectedRegions | index($region))] | length
      '
    )"
  fi

  echo "${response}" | jq -r '
    .data
    | to_entries[]
    | [
        "rank=\(.key + 1)",
        "id=\(.value.id)",
        "role=\(.value.role)",
        "career=\(.value.careerLevel)",
        "region=\(.value.locationRegion // "")",
        "title=\(.value.title)",
        "score=\(.value.score // "")"
      ]
    | @tsv
  '
  echo "expected_roles=${expected_roles_csv}"
  echo "expected_role_hits=${expected_role_hits}"
  if [[ -n "${expected_careers_csv}" ]]; then
    echo "expected_careers=${expected_careers_csv}"
    echo "expected_career_hits=${expected_career_hits}"
  fi
  if [[ -n "${expected_regions_csv}" ]]; then
    echo "expected_regions=${expected_regions_csv}"
    echo "expected_region_hits=${expected_region_hits}"
  fi
  echo

  assert_greater_or_equal \
    "${expected_role_hits}" \
    "${MIN_EXPECTED_ROLE_HITS}" \
    "Search intent '${keyword}' should include expected role in top ${LIMIT}"

  if [[ -n "${expected_careers_csv}" ]]; then
    assert_greater_or_equal \
      "${expected_career_hits}" \
      "${MIN_EXPECTED_CAREER_HITS}" \
      "Search intent '${keyword}' should include expected career level in top ${LIMIT}"
  fi

  if [[ -n "${expected_regions_csv}" ]]; then
    assert_greater_or_equal \
      "${expected_region_hits}" \
      "${MIN_EXPECTED_REGION_HITS}" \
      "Search intent '${keyword}' should include expected region in top ${LIMIT}"
  fi

  passed_query_count=$((passed_query_count + 1))
done

echo "### Search Intent Smoke Summary"
echo "total_query_count=${total_query_count}"
echo "passed_query_count=${passed_query_count}"
echo "limit=${LIMIT}"
echo
echo "Search intent smoke completed."
