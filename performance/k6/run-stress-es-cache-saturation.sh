#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SERVER_URL="${SERVER_URL:-${BASE_URL%/api}}"
HEALTH_URL="${HEALTH_URL:-${SERVER_URL}/actuator/health/liveness}"
PROMETHEUS_URL="${PROMETHEUS_URL:-${SERVER_URL}/actuator/prometheus}"
SEARCH_PREFLIGHT_URL="${SEARCH_PREFLIGHT_URL:-${BASE_URL}/jobs/search?keyword=Spring%20Boot&limit=1}"
HOST_ELASTICSEARCH_URL="${HOST_ELASTICSEARCH_URL:-http://localhost:9200}"
HOT_KEYWORDS="${HOT_KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
TARGET_RPS_LIST="${TARGET_RPS_LIST:-1000,2000,3000}"
DURATION="${DURATION:-2m}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-800}"
MAX_VUS="${MAX_VUS:-4000}"
SEARCH_LIMIT="${SEARCH_LIMIT:-10}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-1000}"
FAIL_RATE_THRESHOLD="${FAIL_RATE_THRESHOLD:-0.01}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
SUMMARY_PREFIX="${SUMMARY_PREFIX:-$(date +%y%m%d)_k6_es_cache_saturation_200k}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
REQUIRE_BACKEND_CACHE_ENABLED="${REQUIRE_BACKEND_CACHE_ENABLED:-true}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-5}"
WARMUP_LIMIT="${WARMUP_LIMIT:-10}"
MIN_CACHE_HIT_DELTA="${MIN_CACHE_HIT_DELTA:-1}"

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
echo "HOT_KEYWORDS=$HOT_KEYWORDS"
echo "TARGET_RPS_LIST=$TARGET_RPS_LIST"
echo "DURATION=$DURATION"
echo "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS"
echo "MAX_VUS=$MAX_VUS"
echo "SEARCH_LIMIT=$SEARCH_LIMIT"
echo "P95_THRESHOLD_MS=$P95_THRESHOLD_MS"
echo "FAIL_RATE_THRESHOLD=$FAIL_RATE_THRESHOLD"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "SUMMARY_PREFIX=$SUMMARY_PREFIX"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "REQUIRE_BACKEND_CACHE_ENABLED=$REQUIRE_BACKEND_CACHE_ENABLED"
echo "WARMUP_ROUNDS=$WARMUP_ROUNDS"
echo "WARMUP_LIMIT=$WARMUP_LIMIT"
echo "MIN_CACHE_HIT_DELTA=$MIN_CACHE_HIT_DELTA"
echo "CACHE_ENABLED=true (set on server via env)"
echo "REINDEX_EXPECTATION=ELASTICSEARCH_REINDEX_ON_STARTUP=false after 200k index preparation"
echo "WORKLOAD=saturation constant-arrival-rate without per-iteration sleep"

urlencode() {
    jq -rn --arg value "$1" '$value|@uri'
}

cache_metric_total() {
    local result="$1"
    curl -fsS "$PROMETHEUS_URL" 2>/dev/null | awk -v result="$result" '
        $0 !~ /^#/ &&
        $0 ~ /^cache_gets_total/ &&
        index($0, "result=\"" result "\"") > 0 &&
        (index($0, "cache=\"jobSearch\"") > 0 || index($0, "name=\"jobSearch\"") > 0) {
            total += $NF
        }
        END {
            printf "%.0f", total + 0
        }
    '
}

backend_env_value() {
    local name="$1"
    docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T backend \
        sh -lc "printenv ${name} || true" 2>/dev/null | tr -d '\r'
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

if [[ "$REQUIRE_BACKEND_CACHE_ENABLED" == "true" ]]; then
    cache_enabled_value="$(backend_env_value CACHE_ENABLED)"
    perf_cache_enabled_value="$(backend_env_value PERF_CACHE_ENABLED)"

    if [[ "$cache_enabled_value" != "true" && "$perf_cache_enabled_value" != "true" ]]; then
        echo "Backend cache preflight failed: expected CACHE_ENABLED=true or PERF_CACHE_ENABLED=true in the running backend container." >&2
        echo "Current values: CACHE_ENABLED=${cache_enabled_value:-empty}, PERF_CACHE_ENABLED=${perf_cache_enabled_value:-empty}" >&2
        echo "Restart the stack with:" >&2
        echo 'PERF_CACHE_ENABLED=true PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED=false ELASTICSEARCH_REINDEX_ON_STARTUP=false PERF_ELASTICSEARCH_MEMORY_LIMIT=3g PERF_ES_JAVA_OPTS="-Xms2g -Xmx2g" REQUIRED_PORTS="" bash performance/deploy/staging-performance-up.sh' >&2
        exit 1
    fi

    echo "backend_cache_enabled=true"
else
    echo "backend_cache_enabled=skipped"
fi

if [[ -z "$ACCESS_TOKEN" ]]; then
    if login_body="$(curl -fsS -X POST "${BASE_URL}/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" 2>/dev/null)"; then
        ACCESS_TOKEN="$(jq -r '.data.accessToken // empty' <<<"$login_body")"
        if [[ -z "$ACCESS_TOKEN" ]]; then
            echo "Auth preflight: login succeeded but no accessToken in response - continuing unauthenticated" >&2
            echo "$login_body" >&2
        fi
    else
        echo "Auth preflight: login failed - continuing unauthenticated (search preflight will verify reachability)" >&2
    fi
fi

echo "auth_preflight=ok"

auth_header=()
if [[ -n "$ACCESS_TOKEN" ]]; then
    auth_header=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
fi

cache_hits_before="$(cache_metric_total hit)"
cache_misses_before="$(cache_metric_total miss)"

if ! search_body="$(curl -fsS "${auth_header[@]}" "$SEARCH_PREFLIGHT_URL" 2>/dev/null)"; then
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

IFS=',' read -r -a keyword_array <<<"$HOT_KEYWORDS"
warmup_request_count=0

for ((round = 1; round <= WARMUP_ROUNDS; round++)); do
    for keyword in "${keyword_array[@]}"; do
        keyword="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$keyword")"
        [[ -z "$keyword" ]] && continue

        encoded_keyword="$(urlencode "$keyword")"
        warmup_url="${BASE_URL}/jobs/search?keyword=${encoded_keyword}&limit=${WARMUP_LIMIT}"

        if ! curl -fsS "${auth_header[@]}" "$warmup_url" >/dev/null 2>&1; then
            echo "Cache warmup request failed: $warmup_url" >&2
            exit 1
        fi

        warmup_request_count=$((warmup_request_count + 1))
    done
done

echo "job_search_cache_warmup=ok rounds=${WARMUP_ROUNDS} requests=${warmup_request_count}"

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

if (( cache_hits_delta < MIN_CACHE_HIT_DELTA )); then
    echo "Job search cache preflight failed: expected at least ${MIN_CACHE_HIT_DELTA} jobSearch cache hit(s) after warmup requests." >&2
    echo "Check that the server was started with PERF_CACHE_ENABLED=true and Redis is reachable." >&2
    exit 1
fi

echo "job_search_cache_preflight=ok"

run_k6() {
    local target_rps="$1"
    local summary_file="${SUMMARY_PREFIX}_${target_rps}rps.json"

    echo
    echo "================================================================================"
    echo "### Saturation run: ${target_rps} RPS"
    echo "================================================================================"
    echo "TARGET_RPS=${target_rps}"
    echo "summary_file=${ARTIFACT_DIR}/${summary_file}"

    if command -v k6 >/dev/null 2>&1; then
        BASE_URL="$BASE_URL" \
        HOT_KEYWORDS="$HOT_KEYWORDS" \
        TARGET_RPS="$target_rps" \
        DURATION="$DURATION" \
        PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
        MAX_VUS="$MAX_VUS" \
        SEARCH_LIMIT="$SEARCH_LIMIT" \
        P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
        FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
        ACCESS_TOKEN="$ACCESS_TOKEN" \
        k6 run \
            --summary-export "$ARTIFACT_DIR/$summary_file" \
            performance/k6/stress-es-cache-saturation-200k.js
    else
        local docker_base_url="$BASE_URL"
        if [[ "$docker_base_url" == http://localhost:* ]]; then
            docker_base_url="${docker_base_url/http:\/\/localhost/http:\/\/host.docker.internal}"
        fi

        echo "k6 binary not found; running Docker fallback with BASE_URL=$docker_base_url"
        chmod 777 "$ARTIFACT_DIR"

        docker run --rm \
            --add-host=host.docker.internal:host-gateway \
            -e BASE_URL="$docker_base_url" \
            -e HOT_KEYWORDS="$HOT_KEYWORDS" \
            -e TARGET_RPS="$target_rps" \
            -e DURATION="$DURATION" \
            -e PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
            -e MAX_VUS="$MAX_VUS" \
            -e SEARCH_LIMIT="$SEARCH_LIMIT" \
            -e P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
            -e FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
            -e ACCESS_TOKEN="$ACCESS_TOKEN" \
            -v "$ROOT_DIR/performance/k6:/scripts:ro" \
            -v "$ARTIFACT_DIR:/k6-output" \
            grafana/k6 run \
                --summary-export "/k6-output/$summary_file" \
                /scripts/stress-es-cache-saturation-200k.js
    fi

    echo "saturation_summary_export=${ARTIFACT_DIR}/${summary_file}"
}

IFS=',' read -r -a target_rps_array <<<"$TARGET_RPS_LIST"
for target_rps in "${target_rps_array[@]}"; do
    target_rps="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$target_rps")"
    [[ -z "$target_rps" ]] && continue
    if ! [[ "$target_rps" =~ ^[0-9]+$ ]]; then
        echo "Invalid TARGET_RPS value: $target_rps" >&2
        exit 1
    fi
    run_k6 "$target_rps"
done

echo
echo "Saturation stress test completed."
echo "summary_prefix=$ARTIFACT_DIR/$SUMMARY_PREFIX"
