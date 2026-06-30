#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SERVER_URL="${SERVER_URL:-${BASE_URL%/api}}"
HEALTH_URL="${HEALTH_URL:-${SERVER_URL}/actuator/health/liveness}"
PROMETHEUS_URL="${PROMETHEUS_URL:-${SERVER_URL}/actuator/prometheus}"
SEARCH_PREFLIGHT_URL="${SEARCH_PREFLIGHT_URL:-${BASE_URL}/jobs/search?keyword=Spring%20Boot&limit=1}"
HOST_ELASTICSEARCH_URL="${HOST_ELASTICSEARCH_URL:-http://localhost:9200}"
HOT_KEYWORDS="${HOT_KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
TARGET_RPS_LIST="${TARGET_RPS_LIST:-1000,1500,2000,2500,3000}"
DURATION="${DURATION:-5m}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-800}"
MAX_VUS="${MAX_VUS:-4000}"
SEARCH_LIMIT="${SEARCH_LIMIT:-10}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-1000}"
FAIL_RATE_THRESHOLD="${FAIL_RATE_THRESHOLD:-0.01}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
RUN_LOCATION="${RUN_LOCATION:-internal}"
RUN_LABEL="${RUN_LABEL:-capacity_rework}"
SUMMARY_PREFIX="${SUMMARY_PREFIX:-$(date +%y%m%d)_k6_es_cache_capacity_${RUN_LABEL}_${RUN_LOCATION}_200k}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
REQUIRE_BACKEND_CACHE_ENABLED="${REQUIRE_BACKEND_CACHE_ENABLED:-true}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-5}"
WARMUP_LIMIT="${WARMUP_LIMIT:-10}"
MIN_CACHE_HIT_DELTA="${MIN_CACHE_HIT_DELTA:-1}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-root}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-root}"
EXPECTED_PERF_JOB_COUNT="${EXPECTED_PERF_JOB_COUNT:-200000}"
EXPECTED_ES_DOC_COUNT="${EXPECTED_ES_DOC_COUNT:-200000}"
ELASTICSEARCH_JOBS_ALIAS="${ELASTICSEARCH_JOBS_ALIAS:-jobflow-jobs-performance}"
PROMETHEUS_SNAPSHOT_METRICS="${PROMETHEUS_SNAPSHOT_METRICS:-cache_gets_total|http_server_requests_seconds|http_server_requests_active_seconds|hikaricp_connections|jvm_memory_used_bytes|process_cpu_usage|system_cpu_usage}"
LOCAL_COMPOSE_PREFLIGHT="${LOCAL_COMPOSE_PREFLIGHT:-auto}"
ES_COUNT_PREFLIGHT="${ES_COUNT_PREFLIGHT:-auto}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$ARTIFACT_DIR"
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"

if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "auto" ]]; then
    if [[ "$BASE_URL" == http://localhost:* || "$BASE_URL" == http://127.0.0.1:* ]]; then
        LOCAL_COMPOSE_PREFLIGHT="true"
    else
        LOCAL_COMPOSE_PREFLIGHT="false"
    fi
fi

if [[ "$ES_COUNT_PREFLIGHT" == "auto" ]]; then
    if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "true" ]]; then
        ES_COUNT_PREFLIGHT="true"
    else
        ES_COUNT_PREFLIGHT="false"
    fi
fi

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
echo "RUN_LOCATION=$RUN_LOCATION"
echo "RUN_LABEL=$RUN_LABEL"
echo "SUMMARY_PREFIX=$SUMMARY_PREFIX"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "REQUIRE_BACKEND_CACHE_ENABLED=$REQUIRE_BACKEND_CACHE_ENABLED"
echo "WARMUP_ROUNDS=$WARMUP_ROUNDS"
echo "WARMUP_LIMIT=$WARMUP_LIMIT"
echo "MIN_CACHE_HIT_DELTA=$MIN_CACHE_HIT_DELTA"
echo "EXPECTED_PERF_JOB_COUNT=$EXPECTED_PERF_JOB_COUNT"
echo "EXPECTED_ES_DOC_COUNT=$EXPECTED_ES_DOC_COUNT"
echo "ELASTICSEARCH_JOBS_ALIAS=$ELASTICSEARCH_JOBS_ALIAS"
echo "LOCAL_COMPOSE_PREFLIGHT=$LOCAL_COMPOSE_PREFLIGHT"
echo "ES_COUNT_PREFLIGHT=$ES_COUNT_PREFLIGHT"
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
    if [[ "$LOCAL_COMPOSE_PREFLIGHT" != "true" ]]; then
        return 0
    fi

    docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T backend \
        sh -lc "printenv ${name} || true" 2>/dev/null | tr -d '\r'
}

compose_exec() {
    if [[ "$LOCAL_COMPOSE_PREFLIGHT" != "true" ]]; then
        return 1
    fi

    docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T "$@"
}

prometheus_snapshot() {
    local output_file="$1"

    if curl -fsS "$PROMETHEUS_URL" 2>/dev/null |
        grep -E "$PROMETHEUS_SNAPSHOT_METRICS" >"$output_file"; then
        echo "prometheus_snapshot=$output_file"
    else
        echo "prometheus_snapshot=empty_or_failed file=$output_file" >&2
        : >"$output_file"
    fi
}

perf_fixture_count() {
    compose_exec "$MYSQL_SERVICE" \
        mysql -u "$PERF_DB_USER" "-p${PERF_DB_PASSWORD}" \
        --batch --raw --skip-column-names "$PERF_DB_NAME" \
        -e "SELECT COUNT(*) FROM jobs WHERE source='perf_fixture';" 2>/dev/null | tr -d '\r'
}

es_doc_count() {
    curl -fsS "${HOST_ELASTICSEARCH_URL}/${ELASTICSEARCH_JOBS_ALIAS}/_count" 2>/dev/null |
        jq -r '.count // 0'
}

if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "true" ]]; then
    perf_job_count="$(perf_fixture_count || true)"
    if [[ "$perf_job_count" != "$EXPECTED_PERF_JOB_COUNT" ]]; then
        echo "Performance fixture preflight failed: expected ${EXPECTED_PERF_JOB_COUNT}, got ${perf_job_count:-empty}" >&2
        echo "Check ${PERF_DB_NAME}.jobs and rerun performance dataset preparation if needed." >&2
        exit 1
    fi

    echo "perf_fixture_job_count=$perf_job_count"
else
    echo "perf_fixture_job_count=skipped reason=LOCAL_COMPOSE_PREFLIGHT_false"
fi

if [[ "$ES_COUNT_PREFLIGHT" == "true" ]]; then
    es_count="$(es_doc_count || true)"
    if [[ "$es_count" != "$EXPECTED_ES_DOC_COUNT" ]]; then
        echo "Elasticsearch performance index preflight failed: expected ${EXPECTED_ES_DOC_COUNT}, got ${es_count:-empty}" >&2
        echo "Check ${HOST_ELASTICSEARCH_URL}/${ELASTICSEARCH_JOBS_ALIAS}/_count and performance profile reindex settings." >&2
        exit 1
    fi

    echo "es_performance_doc_count=$es_count"
else
    echo "es_performance_doc_count=skipped reason=ES_COUNT_PREFLIGHT_false"
fi

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
    if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "true" ]]; then
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
        echo "backend_cache_enabled=not_checked_directly reason=LOCAL_COMPOSE_PREFLIGHT_false"
        echo "backend_cache_enabled_validation=cache_hit_delta_preflight"
    fi
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
    local prom_before="${SUMMARY_PREFIX}_${target_rps}rps_prometheus_before.prom"
    local prom_after="${SUMMARY_PREFIX}_${target_rps}rps_prometheus_after.prom"

    echo
    echo "================================================================================"
    echo "### Saturation run: ${target_rps} RPS"
    echo "================================================================================"
    echo "TARGET_RPS=${target_rps}"
    echo "summary_file=${ARTIFACT_DIR}/${summary_file}"
    echo "prometheus_before_file=${ARTIFACT_DIR}/${prom_before}"
    prometheus_snapshot "${ARTIFACT_DIR}/${prom_before}"

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

    echo "prometheus_after_file=${ARTIFACT_DIR}/${prom_after}"
    prometheus_snapshot "${ARTIFACT_DIR}/${prom_after}"
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
