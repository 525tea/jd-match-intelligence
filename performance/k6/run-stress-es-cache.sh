#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SERVER_URL="${SERVER_URL:-${BASE_URL%/api}}"
HEALTH_URL="${HEALTH_URL:-${SERVER_URL}/actuator/health/liveness}"
PROMETHEUS_URL="${PROMETHEUS_URL:-${SERVER_URL}/actuator/prometheus}"
SEARCH_PREFLIGHT_URL="${SEARCH_PREFLIGHT_URL:-${BASE_URL}/jobs/search?keyword=Spring%20Boot&limit=1}"
HOST_ELASTICSEARCH_URL="${HOST_ELASTICSEARCH_URL:-http://localhost:9200}"
KEYWORDS="${KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
SUMMARY_FILE="${SUMMARY_FILE:-$(date +%y%m%d)_k6_es_cache_200k_500vu.json}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"

echo "BASE_URL=$BASE_URL"
echo "HEALTH_URL=$HEALTH_URL"
echo "PROMETHEUS_URL=$PROMETHEUS_URL"
echo "SEARCH_PREFLIGHT_URL=$SEARCH_PREFLIGHT_URL"
echo "HOST_ELASTICSEARCH_URL=$HOST_ELASTICSEARCH_URL"
echo "TARGET=backend-direct"
echo "KEYWORDS=$KEYWORDS"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "SUMMARY_FILE=$SUMMARY_FILE"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "CACHE_ENABLED=true (set on server via env)"
echo "REINDEX_EXPECTATION=ELASTICSEARCH_REINDEX_ON_STARTUP=false after 200k index preparation"

cache_metric_total() {
    local result="$1"
    curl -fsS "$PROMETHEUS_URL" 2>/dev/null | awk -v result="$result" '
        $0 !~ /^#/ &&
        $0 ~ /^cache_gets_total/ &&
        $0 ~ /result="/ result "/ &&
        ($0 ~ /cache="jobSearch"/ || $0 ~ /name="jobSearch"/) {
            total += $NF
        }
        END {
            printf "%.0f", total + 0
        }
    '
}

if ! health_body="$(curl -fsS "$HEALTH_URL" 2>/dev/null)"; then
    echo "Backend health preflight failed: $HEALTH_URL" >&2
    echo "Full health response:" >&2
    curl -s "${SERVER_URL}/actuator/health" >&2 || true
    echo >&2
    exit 1
fi

echo "backend_health_preflight=$health_body"

if ! curl -fsS "$PROMETHEUS_URL" >/dev/null 2>&1; then
    echo "Prometheus preflight failed: $PROMETHEUS_URL" >&2
    echo "Check that /actuator/prometheus is exposed and reachable on the backend direct port." >&2
    exit 1
fi

echo "prometheus_preflight=ok"

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

cache_hits_before="$(cache_metric_total hit)"
cache_misses_before="$(cache_metric_total miss)"

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

if ! curl -fsS -H "Authorization: Bearer ${ACCESS_TOKEN}" "$SEARCH_PREFLIGHT_URL" >/dev/null 2>&1; then
    echo "Second cache preflight request failed: $SEARCH_PREFLIGHT_URL" >&2
    exit 1
fi

cache_hits_after="$(cache_metric_total hit)"
cache_misses_after="$(cache_metric_total miss)"
cache_hits_delta=$((cache_hits_after - cache_hits_before))
cache_misses_delta=$((cache_misses_after - cache_misses_before))

echo "job_search_cache_hits_before=$cache_hits_before"
echo "job_search_cache_hits_after=$cache_hits_after"
echo "job_search_cache_hits_delta=$cache_hits_delta"
echo "job_search_cache_misses_before=$cache_misses_before"
echo "job_search_cache_misses_after=$cache_misses_after"
echo "job_search_cache_misses_delta=$cache_misses_delta"

if (( cache_hits_delta < 1 )); then
    echo "Job search cache preflight failed: expected at least one jobSearch cache hit after two identical search requests." >&2
    echo "Check that the server was started with PERF_CACHE_ENABLED=true and Redis is reachable." >&2
    echo "Expected server startup:" >&2
    echo "PERF_CACHE_ENABLED=true REQUIRED_PORTS=\"\" bash performance/deploy/staging-performance-up.sh" >&2
    exit 1
fi

echo "job_search_cache_preflight=ok"

if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    KEYWORDS="$KEYWORDS" \
    ACCESS_TOKEN="$ACCESS_TOKEN" \
    k6 run \
        --summary-export "$ARTIFACT_DIR/$SUMMARY_FILE" \
        performance/k6/stress-es-cache-200k.js
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
            /scripts/stress-es-cache-200k.js
fi

echo
echo "Stress test completed."
echo "summary_export=$ARTIFACT_DIR/$SUMMARY_FILE"
