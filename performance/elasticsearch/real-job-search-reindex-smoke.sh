#!/usr/bin/env bash

set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
BASE_URL="${BASE_URL:-http://localhost:8080}"
INDEX_ALIAS="${INDEX_ALIAS:-jobflow-jobs}"
LIMIT="${LIMIT:-5}"

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to run this script."
  exit 1
fi

if ! curl -fsS "${ES_URL}/_alias/${INDEX_ALIAS}" >/dev/null; then
  echo "Elasticsearch alias '${INDEX_ALIAS}' does not exist."
  echo "Run performance/elasticsearch/reindex-real-jobs.sh first."
  exit 1
fi

if ! curl -fsS "${BASE_URL}/actuator/health" >/dev/null; then
  echo "Backend is not reachable at ${BASE_URL}."
  echo "Start backend before running this smoke test."
  exit 1
fi

echo "ES_URL=${ES_URL}"
echo "BASE_URL=${BASE_URL}"
echo "INDEX_ALIAS=${INDEX_ALIAS}"
echo "LIMIT=${LIMIT}"
echo

echo "### Alias"
curl -sS "${ES_URL}/_alias/${INDEX_ALIAS}" | jq
echo

echo "### Document Count"
count_response="$(curl -sS "${ES_URL}/${INDEX_ALIAS}/_count")"
echo "${count_response}" | jq

document_count="$(echo "${count_response}" | jq -r '.count')"
if [[ "${document_count}" -le 0 ]]; then
  echo "Elasticsearch alias '${INDEX_ALIAS}' has no indexed documents."
  exit 1
fi

echo

search() {
  local keyword="$1"

  echo "### GET /jobs/search?keyword=${keyword}&limit=${LIMIT}"

  response="$(
    curl -sS -G "${BASE_URL}/jobs/search" \
      --data-urlencode "keyword=${keyword}" \
      --data-urlencode "limit=${LIMIT}"
  )"

  success="$(echo "${response}" | jq -r '.success')"
  if [[ "${success}" != "true" ]]; then
    echo "Search API failed. keyword=${keyword}"
    echo "${response}" | jq
    exit 1
  fi

  result_count="$(echo "${response}" | jq '.data | length')"
  if [[ "${result_count}" -eq 0 ]]; then
    echo "Search API returned no results. keyword=${keyword}"
    echo "${response}" | jq
    exit 1
  fi

  echo "${response}" | jq -r '
    .data[]
    | [
        .id,
        .title,
        .companyName,
        .role,
        (.locationRegion // ""),
        (.locationCity // ""),
        .score
      ]
    | @tsv
  '

  echo
}

search "백엔드 개발자"
search "프론트엔드 React"
search "C++ 개발자"
search "Node.js 백엔드"
search "쿠버네티스 플랫폼"
search "AI 엔지니어"

echo "Real job search reindex smoke completed."
