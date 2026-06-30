#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

BASE_URL="${BASE_URL:-http://127.0.0.1:8081/api}"
LIMIT="${LIMIT:-10}"
FETCH_LIMIT="${FETCH_LIMIT:-40}"
FETCH_DETAILS_FOR_TFIDF="${FETCH_DETAILS_FOR_TFIDF:-true}"
RUN_LABEL="${RUN_LABEL:-search-ndcg10}"
OUTPUT_FILE="${OUTPUT_FILE:-}"
SUMMARY_FILE="${SUMMARY_FILE:-}"
MIN_NDCG="${MIN_NDCG:-0.0}"
FAIL_ON_THRESHOLD="${FAIL_ON_THRESHOLD:-false}"

args=(
  --base-url "${BASE_URL}"
  --limit "${LIMIT}"
  --fetch-limit "${FETCH_LIMIT}"
  --run-label "${RUN_LABEL}"
  --output-file "${OUTPUT_FILE}"
  --summary-file "${SUMMARY_FILE}"
  --min-ndcg "${MIN_NDCG}"
)

if [[ "${FETCH_DETAILS_FOR_TFIDF}" == "true" ]]; then
  args+=(--fetch-details)
else
  args+=(--no-fetch-details)
fi

if [[ "${FAIL_ON_THRESHOLD}" == "true" ]]; then
  args+=(--fail-on-threshold)
else
  args+=(--no-fail-on-threshold)
fi

python3 "${ROOT_DIR}/performance/elasticsearch/search_ndcg10_evaluate.py" "${args[@]}"
