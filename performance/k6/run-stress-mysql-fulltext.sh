#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
KEYWORDS="${KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
ARTIFACT_DIR="${ARTIFACT_DIR:-/tmp/jobflow-k6-artifacts}"
SUMMARY_FILE="${SUMMARY_FILE:-$(date +%Y%m%d)_k6_mysql_200k_500vu.json}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$ARTIFACT_DIR"

echo "BASE_URL=$BASE_URL"
echo "KEYWORDS=$KEYWORDS"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "SUMMARY_FILE=$SUMMARY_FILE"

if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    KEYWORDS="$KEYWORDS" \
    k6 run \
        --summary-export "$ARTIFACT_DIR/$SUMMARY_FILE" \
        performance/k6/stress-mysql-fulltext-200k.js
else
    docker run --rm --network host \
        -e BASE_URL="$BASE_URL" \
        -e KEYWORDS="$KEYWORDS" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$ARTIFACT_DIR:/k6-output" \
        grafana/k6 run \
            --summary-export "/k6-output/$SUMMARY_FILE" \
            /scripts/stress-mysql-fulltext-200k.js
fi

echo
echo "Stress test completed."
echo "summary_export=$ARTIFACT_DIR/$SUMMARY_FILE"
