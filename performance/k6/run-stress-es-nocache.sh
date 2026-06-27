#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
KEYWORDS="${KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
SUMMARY_FILE="${SUMMARY_FILE:-$(date +%y%m%d)_k6_es_nocache_200k_500vu.json}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"

echo "BASE_URL=$BASE_URL"
echo "TARGET=backend-direct"
echo "KEYWORDS=$KEYWORDS"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "SUMMARY_FILE=$SUMMARY_FILE"
echo "CACHE_ENABLED=false (set on server via env)"

if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    KEYWORDS="$KEYWORDS" \
    k6 run \
        --summary-export "$ARTIFACT_DIR/$SUMMARY_FILE" \
        performance/k6/stress-es-nocache-200k.js
else
    DOCKER_BASE_URL="$BASE_URL"
    if [[ "$DOCKER_BASE_URL" == http://localhost:* ]]; then
        DOCKER_BASE_URL="${DOCKER_BASE_URL/http:\/\/localhost/http:\/\/host.docker.internal}"
    fi

    echo "k6 binary not found; running Docker fallback with BASE_URL=$DOCKER_BASE_URL"

    docker run --rm \
        --add-host=host.docker.internal:host-gateway \
        -e BASE_URL="$DOCKER_BASE_URL" \
        -e KEYWORDS="$KEYWORDS" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$ARTIFACT_DIR:/k6-output" \
        grafana/k6 run \
            --summary-export "/k6-output/$SUMMARY_FILE" \
            /scripts/stress-es-nocache-200k.js
fi

echo
echo "Stress test completed."
echo "summary_export=$ARTIFACT_DIR/$SUMMARY_FILE"
