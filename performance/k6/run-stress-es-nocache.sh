#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SERVER_URL="${SERVER_URL:-${BASE_URL%/api}}"
HEALTH_URL="${HEALTH_URL:-${SERVER_URL}/actuator/health}"
SEARCH_PREFLIGHT_URL="${SEARCH_PREFLIGHT_URL:-${BASE_URL}/jobs/search?keyword=Spring%20Boot&limit=1}"
HOST_ELASTICSEARCH_URL="${HOST_ELASTICSEARCH_URL:-http://localhost:9200}"
KEYWORDS="${KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
SUMMARY_FILE="${SUMMARY_FILE:-$(date +%y%m%d)_k6_es_nocache_200k_500vu.json}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"

echo "BASE_URL=$BASE_URL"
echo "HEALTH_URL=$HEALTH_URL"
echo "SEARCH_PREFLIGHT_URL=$SEARCH_PREFLIGHT_URL"
echo "HOST_ELASTICSEARCH_URL=$HOST_ELASTICSEARCH_URL"
echo "TARGET=backend-direct"
echo "KEYWORDS=$KEYWORDS"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "SUMMARY_FILE=$SUMMARY_FILE"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "CACHE_ENABLED=false (set on server via env)"
echo "REINDEX_EXPECTATION=ELASTICSEARCH_REINDEX_ON_STARTUP=false after 200k index preparation"

if ! health_body="$(curl -fsS "$HEALTH_URL" 2>/dev/null)"; then
    echo "Backend health preflight failed: $HEALTH_URL" >&2
    echo "Full health response:" >&2
    curl -s "${SERVER_URL}/actuator/health" >&2 || true
    echo >&2
    exit 1
fi

echo "backend_health_preflight=$health_body"

if [[ -z "$ACCESS_TOKEN" ]]; then
    if login_body="$(curl -fsS -X POST "${BASE_URL}/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" 2>/dev/null)"; then
        ACCESS_TOKEN="$(jq -r '.data.accessToken // empty' <<<"$login_body")"
        if [[ -z "$ACCESS_TOKEN" ]]; then
            echo "Auth preflight: login succeeded but no accessToken in response — continuing unauthenticated" >&2
            echo "$login_body" >&2
        fi
    else
        echo "Auth preflight: login failed — continuing unauthenticated (search preflight will verify reachability)" >&2
    fi
fi

echo "auth_preflight=ok"

if ! search_body="$(curl -fsS -H "Authorization: Bearer ${ACCESS_TOKEN}" "$SEARCH_PREFLIGHT_URL" 2>/dev/null)"; then
    echo "Search preflight failed: $SEARCH_PREFLIGHT_URL" >&2
    echo "Elasticsearch cluster health:" >&2
    curl -s "${HOST_ELASTICSEARCH_URL}/_cluster/health?pretty" >&2 || true
    echo >&2
    exit 1
fi

if ! grep -Eq '"success"[[:space:]]*:[[:space:]]*true' <<<"$search_body"; then
    echo "Search preflight returned an unexpected response:" >&2
    echo "$search_body" >&2
    exit 1
fi

echo "search_preflight=ok"

if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    KEYWORDS="$KEYWORDS" \
    ACCESS_TOKEN="$ACCESS_TOKEN" \
    k6 run \
        --summary-export "$ARTIFACT_DIR/$SUMMARY_FILE" \
        performance/k6/stress-es-nocache-200k.js
else
    DOCKER_BASE_URL="$BASE_URL"
    if [[ "$DOCKER_BASE_URL" == http://localhost:* ]]; then
        DOCKER_BASE_URL="${DOCKER_BASE_URL/http:\/\/localhost/http:\/\/host.docker.internal}"
    fi

    echo "k6 binary not found; running Docker fallback with BASE_URL=$DOCKER_BASE_URL"
    chmod 777 "$ARTIFACT_DIR"

    docker run --rm \
        --add-host=host.docker.internal:host-gateway \
        -e BASE_URL="$DOCKER_BASE_URL" \
        -e KEYWORDS="$KEYWORDS" \
        -e ACCESS_TOKEN="$ACCESS_TOKEN" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$ARTIFACT_DIR:/k6-output" \
        grafana/k6 run \
            --summary-export "/k6-output/$SUMMARY_FILE" \
            /scripts/stress-es-nocache-200k.js
fi

echo
echo "Stress test completed."
echo "summary_export=$ARTIFACT_DIR/$SUMMARY_FILE"
