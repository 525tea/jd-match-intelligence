#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
MONTH="${MONTH:-$(date +%Y-%m-01)}"
LIMIT="${LIMIT:-10}"
ROLE="${ROLE:-BACKEND}"
SKILL_ID="${SKILL_ID:-}"

request() {
  local title="$1"
  local path="$2"
  shift 2

  echo "### ${title}"
  curl --fail --show-error --silent --max-time 10 -G "${BASE_URL}${path}" "$@"
  echo
  echo
}

echo "BASE_URL=${BASE_URL}"
echo "MONTH=${MONTH}"
echo "LIMIT=${LIMIT}"
echo

request "GET /trends/skills" "/trends/skills" \
  --data-urlencode "month=${MONTH}" \
  --data-urlencode "limit=${LIMIT}"

if [[ -n "${SKILL_ID}" ]]; then
  request "GET /trends/skills/${SKILL_ID}/cooccurrences" "/trends/skills/${SKILL_ID}/cooccurrences" \
    --data-urlencode "month=${MONTH}" \
    --data-urlencode "limit=${LIMIT}"

  request "GET /trends/skills/${SKILL_ID}/experience-tags" "/trends/skills/${SKILL_ID}/experience-tags" \
    --data-urlencode "month=${MONTH}" \
    --data-urlencode "limit=${LIMIT}"
else
  echo "### SKIP skill detail trend APIs"
  echo "Set SKILL_ID to check cooccurrences and experience-tags."
  echo
fi

request "GET /trends/market" "/trends/market" \
  --data-urlencode "month=${MONTH}" \
  --data-urlencode "role=${ROLE}" \
  --data-urlencode "limit=${LIMIT}"
