#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SERVER_URL="${SERVER_URL:-${BASE_URL%/api}}"
HEALTH_URL="${HEALTH_URL:-${SERVER_URL}/actuator/health/liveness}"
PROMETHEUS_URL="${PROMETHEUS_URL:-${SERVER_URL}/actuator/prometheus}"
SEARCH_PREFLIGHT_URL="${SEARCH_PREFLIGHT_URL:-${BASE_URL}/jobs/search?keyword=Spring%20Boot&limit=1}"
HOST_ELASTICSEARCH_URL="${HOST_ELASTICSEARCH_URL:-http://localhost:9200}"
HOT_KEYWORDS="${HOT_KEYWORDS:-백엔드,Spring Boot,프론트엔드,React,데이터 엔지니어,DevOps,Kubernetes,Python,Java,TypeScript}"
CAPACITY_SCENARIOS="${CAPACITY_SCENARIOS:-hot,cold,mixed,spike}"
TARGET_RPS_LIST="${TARGET_RPS_LIST:-1000}"
BASE_RPS="${BASE_RPS:-500}"
DURATION="${DURATION:-2m}"
SPIKE_WARMUP_DURATION="${SPIKE_WARMUP_DURATION:-30s}"
SPIKE_RAMP_DURATION="${SPIKE_RAMP_DURATION:-10s}"
SPIKE_HOLD_DURATION="${SPIKE_HOLD_DURATION:-2m}"
SPIKE_RECOVERY_DURATION="${SPIKE_RECOVERY_DURATION:-30s}"
RAMP_DOWN_DURATION="${RAMP_DOWN_DURATION:-30s}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-800}"
MAX_VUS="${MAX_VUS:-4000}"
SEARCH_LIMIT="${SEARCH_LIMIT:-10}"
HOT_TRAFFIC_PERCENT="${HOT_TRAFFIC_PERCENT:-70}"
SPIKE_TRAFFIC_PROFILE="${SPIKE_TRAFFIC_PROFILE:-hot}"
LONG_TAIL_PREFIX="${LONG_TAIL_PREFIX:-capacity-tail}"
LONG_TAIL_VARIANTS="${LONG_TAIL_VARIANTS:-1000000}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-1000}"
FAIL_RATE_THRESHOLD="${FAIL_RATE_THRESHOLD:-0.01}"
MAX_DROPPED_ITERATIONS="${MAX_DROPPED_ITERATIONS:-0}"
K6_NOFILE_LIMIT="${K6_NOFILE_LIMIT:-65535}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
RUN_LOCATION="${RUN_LOCATION:-internal}"
RUN_LABEL="${RUN_LABEL:-capacity_followup}"
SUMMARY_PREFIX="${SUMMARY_PREFIX:-$(date +%y%m%d)_k6_es_cache_capacity_${RUN_LABEL}_${RUN_LOCATION}_200k}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
SEARCH_AUTH_MODE="${SEARCH_AUTH_MODE:-public}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
REQUIRE_BACKEND_CACHE_ENABLED="${REQUIRE_BACKEND_CACHE_ENABLED:-true}"
RESET_REDIS_CACHE_BEFORE_RUN="${RESET_REDIS_CACHE_BEFORE_RUN:-true}"
CACHE_RESET_MODE="${CACHE_RESET_MODE:-auto}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-5}"
WARMUP_LIMIT="${WARMUP_LIMIT:-10}"
MIN_CACHE_HIT_DELTA="${MIN_CACHE_HIT_DELTA:-1}"
MIN_CACHE_MISS_DELTA="${MIN_CACHE_MISS_DELTA:-1}"
MAX_COLD_CACHE_HIT_DELTA="${MAX_COLD_CACHE_HIT_DELTA:-0}"
MAX_HOT_CACHE_MISS_DELTA="${MAX_HOT_CACHE_MISS_DELTA:-0}"
MIXED_HIT_RATE_TOLERANCE_PERCENT="${MIXED_HIT_RATE_TOLERANCE_PERCENT:-15}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-root}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-root}"
EXPECTED_PERF_JOB_COUNT="${EXPECTED_PERF_JOB_COUNT:-200000}"
EXPECTED_ES_DOC_COUNT="${EXPECTED_ES_DOC_COUNT:-200000}"
ELASTICSEARCH_JOBS_ALIAS="${ELASTICSEARCH_JOBS_ALIAS:-jobflow-jobs-performance}"
PROMETHEUS_SNAPSHOT_METRICS="${PROMETHEUS_SNAPSHOT_METRICS:-cache_gets_total|http_server_requests_seconds|http_server_requests_active_seconds|hikaricp_connections|jvm_memory_used_bytes|process_cpu_usage|system_cpu_usage}"
LOCAL_COMPOSE_PREFLIGHT="${LOCAL_COMPOSE_PREFLIGHT:-auto}"
ES_COUNT_PREFLIGHT="${ES_COUNT_PREFLIGHT:-auto}"
FAIL_FAST="${FAIL_FAST:-true}"
ALLOW_MULTI_CASE_WITHOUT_RESET="${ALLOW_MULTI_CASE_WITHOUT_RESET:-false}"

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

if [[ "$CACHE_RESET_MODE" == "auto" ]]; then
    if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "true" ]]; then
        CACHE_RESET_MODE="compose"
    else
        CACHE_RESET_MODE="skip"
    fi
fi

echo "BASE_URL=$BASE_URL"
echo "HEALTH_URL=$HEALTH_URL"
echo "PROMETHEUS_URL=$PROMETHEUS_URL"
echo "SEARCH_PREFLIGHT_URL=$SEARCH_PREFLIGHT_URL"
echo "HOST_ELASTICSEARCH_URL=$HOST_ELASTICSEARCH_URL"
echo "TARGET=backend-direct"
echo "HOT_KEYWORDS=$HOT_KEYWORDS"
echo "CAPACITY_SCENARIOS=$CAPACITY_SCENARIOS"
echo "TARGET_RPS_LIST=$TARGET_RPS_LIST"
echo "BASE_RPS=$BASE_RPS"
echo "DURATION=$DURATION"
echo "SPIKE_WARMUP_DURATION=$SPIKE_WARMUP_DURATION"
echo "SPIKE_RAMP_DURATION=$SPIKE_RAMP_DURATION"
echo "SPIKE_HOLD_DURATION=$SPIKE_HOLD_DURATION"
echo "SPIKE_RECOVERY_DURATION=$SPIKE_RECOVERY_DURATION"
echo "RAMP_DOWN_DURATION=$RAMP_DOWN_DURATION"
echo "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS"
echo "MAX_VUS=$MAX_VUS"
echo "SEARCH_LIMIT=$SEARCH_LIMIT"
echo "HOT_TRAFFIC_PERCENT=$HOT_TRAFFIC_PERCENT"
echo "SPIKE_TRAFFIC_PROFILE=$SPIKE_TRAFFIC_PROFILE"
echo "LONG_TAIL_PREFIX=$LONG_TAIL_PREFIX"
echo "LONG_TAIL_VARIANTS=$LONG_TAIL_VARIANTS"
echo "P95_THRESHOLD_MS=$P95_THRESHOLD_MS"
echo "FAIL_RATE_THRESHOLD=$FAIL_RATE_THRESHOLD"
echo "MAX_DROPPED_ITERATIONS=$MAX_DROPPED_ITERATIONS"
echo "K6_NOFILE_LIMIT=$K6_NOFILE_LIMIT"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "RUN_LOCATION=$RUN_LOCATION"
echo "RUN_LABEL=$RUN_LABEL"
echo "SUMMARY_PREFIX=$SUMMARY_PREFIX"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "SEARCH_AUTH_MODE=$SEARCH_AUTH_MODE"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "REQUIRE_BACKEND_CACHE_ENABLED=$REQUIRE_BACKEND_CACHE_ENABLED"
echo "RESET_REDIS_CACHE_BEFORE_RUN=$RESET_REDIS_CACHE_BEFORE_RUN"
echo "CACHE_RESET_MODE=$CACHE_RESET_MODE"
echo "WARMUP_ROUNDS=$WARMUP_ROUNDS"
echo "WARMUP_LIMIT=$WARMUP_LIMIT"
echo "MIN_CACHE_HIT_DELTA=$MIN_CACHE_HIT_DELTA"
echo "MIN_CACHE_MISS_DELTA=$MIN_CACHE_MISS_DELTA"
echo "MAX_COLD_CACHE_HIT_DELTA=$MAX_COLD_CACHE_HIT_DELTA"
echo "MAX_HOT_CACHE_MISS_DELTA=$MAX_HOT_CACHE_MISS_DELTA"
echo "MIXED_HIT_RATE_TOLERANCE_PERCENT=$MIXED_HIT_RATE_TOLERANCE_PERCENT"
echo "EXPECTED_PERF_JOB_COUNT=$EXPECTED_PERF_JOB_COUNT"
echo "EXPECTED_ES_DOC_COUNT=$EXPECTED_ES_DOC_COUNT"
echo "ELASTICSEARCH_JOBS_ALIAS=$ELASTICSEARCH_JOBS_ALIAS"
echo "LOCAL_COMPOSE_PREFLIGHT=$LOCAL_COMPOSE_PREFLIGHT"
echo "ES_COUNT_PREFLIGHT=$ES_COUNT_PREFLIGHT"
echo "FAIL_FAST=$FAIL_FAST"
echo "ALLOW_MULTI_CASE_WITHOUT_RESET=$ALLOW_MULTI_CASE_WITHOUT_RESET"
echo "CACHE_ENABLED=true (set on server via env)"
echo "REINDEX_EXPECTATION=ELASTICSEARCH_REINDEX_ON_STARTUP=false after 200k index preparation"
echo "WORKLOAD=capacity follow-up with explicit hot/cold/mixed/spike cache state"

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
        echo "compose_exec requires LOCAL_COMPOSE_PREFLIGHT=true" >&2
        return 1
    fi

    docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T "$@"
}

prometheus_snapshot() {
    local output_file="$1"
    curl -fsS "$PROMETHEUS_URL" 2>/dev/null \
        | grep -E "$PROMETHEUS_SNAPSHOT_METRICS" >"$output_file" || true
}

perf_fixture_count() {
    compose_exec "$MYSQL_SERVICE" \
        mysql -u "$PERF_DB_USER" "-p${PERF_DB_PASSWORD}" --batch --raw --skip-column-names "$PERF_DB_NAME" \
        -e "SELECT COUNT(*) FROM jobs WHERE source='perf_fixture';" 2>/dev/null | tr -d '\r'
}

es_doc_count() {
    curl -fsS "${HOST_ELASTICSEARCH_URL}/${ELASTICSEARCH_JOBS_ALIAS}/_count" 2>/dev/null \
        | jq -r '.count // empty'
}

reset_redis_cache() {
    if [[ "$RESET_REDIS_CACHE_BEFORE_RUN" != "true" ]]; then
        echo "redis_cache_reset=skipped reason=RESET_REDIS_CACHE_BEFORE_RUN_false"
        return
    fi

    if [[ "$CACHE_RESET_MODE" != "compose" ]]; then
        echo "redis_cache_reset=failed reason=CACHE_RESET_MODE_${CACHE_RESET_MODE}" >&2
        echo "For external runners, flush Redis on the application host first, then run with RESET_REDIS_CACHE_BEFORE_RUN=false." >&2
        return 1
    fi

    compose_exec "$REDIS_SERVICE" redis-cli FLUSHDB >/dev/null
    echo "redis_cache_reset=ok mode=compose"
}

validate_k6_summary() {
    local summary_path="$1"
    local target_rps="$2"
    local scenario="$3"

    if [[ ! -s "$summary_path" ]]; then
        echo "k6 summary file was not created: $summary_path" >&2
        return 1
    fi

    local dropped_iterations
    dropped_iterations="$(jq -r '.metrics.dropped_iterations.count // 0' "$summary_path")"

    echo "dropped_iterations=${dropped_iterations}"
    if (( dropped_iterations > MAX_DROPPED_ITERATIONS )); then
        echo "Dropped iterations exceeded limit for scenario=${scenario} target_rps=${target_rps}: ${dropped_iterations} > ${MAX_DROPPED_ITERATIONS}" >&2
        return 1
    fi
}

validate_cache_deltas() {
    local scenario="$1"
    local hits_delta="$2"
    local misses_delta="$3"
    local total_delta=$((hits_delta + misses_delta))

    if [[ "$scenario" == "spike" && "$SPIKE_TRAFFIC_PROFILE" == "mixed" ]]; then
        scenario="mixed"
    fi

    case "$scenario" in
        hot|spike)
            if (( hits_delta < MIN_CACHE_HIT_DELTA )); then
                echo "Cache validation failed for ${scenario}: expected hit delta >= ${MIN_CACHE_HIT_DELTA}, got ${hits_delta}" >&2
                return 1
            fi

            if (( misses_delta > MAX_HOT_CACHE_MISS_DELTA )); then
                echo "Cache validation failed for ${scenario}: expected miss delta <= ${MAX_HOT_CACHE_MISS_DELTA}, got ${misses_delta}" >&2
                return 1
            fi
            ;;
        cold)
            if (( misses_delta < MIN_CACHE_MISS_DELTA )); then
                echo "Cache validation failed for cold: expected miss delta >= ${MIN_CACHE_MISS_DELTA}, got ${misses_delta}" >&2
                return 1
            fi

            if (( hits_delta > MAX_COLD_CACHE_HIT_DELTA )); then
                echo "Cache validation failed for cold: expected hit delta <= ${MAX_COLD_CACHE_HIT_DELTA}, got ${hits_delta}" >&2
                return 1
            fi
            ;;
        mixed)
            if (( hits_delta < MIN_CACHE_HIT_DELTA || misses_delta < MIN_CACHE_MISS_DELTA )); then
                echo "Cache validation failed for mixed: expected both hit and miss deltas, got hit=${hits_delta}, miss=${misses_delta}" >&2
                return 1
            fi

            if (( total_delta > 0 )); then
                local lower=$((HOT_TRAFFIC_PERCENT - MIXED_HIT_RATE_TOLERANCE_PERCENT))
                local upper=$((HOT_TRAFFIC_PERCENT + MIXED_HIT_RATE_TOLERANCE_PERCENT))
                (( lower < 0 )) && lower=0
                (( upper > 100 )) && upper=100

                local hit_rate_percent=$((hits_delta * 100 / total_delta))
                echo "job_search_cache_hit_rate_percent=${hit_rate_percent}"

                if (( hit_rate_percent < lower || hit_rate_percent > upper )); then
                    echo "Cache validation failed for mixed: expected hit rate ${lower}-${upper}%, got ${hit_rate_percent}%" >&2
                    return 1
                fi
            fi
            ;;
        *)
            echo "Invalid scenario for cache validation: $scenario" >&2
            return 1
            ;;
    esac
}

needs_hot_warmup() {
    local scenario="$1"
    [[ "$scenario" == "hot" || "$scenario" == "mixed" || "$scenario" == "spike" ]]
}

raise_nofile_limit() {
    local current_limit hard_limit
    current_limit="$(ulimit -Sn)"
    hard_limit="$(ulimit -Hn)"

    if [[ "$current_limit" == "unlimited" || "$K6_NOFILE_LIMIT" == "0" ]]; then
        echo "k6_nofile_limit=skipped current=${current_limit} requested=${K6_NOFILE_LIMIT}"
        return
    fi

    if [[ "$hard_limit" != "unlimited" && "$K6_NOFILE_LIMIT" -gt "$hard_limit" ]]; then
        echo "k6_nofile_limit=skipped current=${current_limit} hard=${hard_limit} requested=${K6_NOFILE_LIMIT}"
        return
    fi

    if (( current_limit < K6_NOFILE_LIMIT )); then
        ulimit -Sn "$K6_NOFILE_LIMIT"
        current_limit="$(ulimit -Sn)"
    fi

    echo "k6_nofile_limit=current:${current_limit},hard:${hard_limit},requested=${K6_NOFILE_LIMIT}"
}

run_k6_binary() {
    local scenario="$1"
    local target_rps="$2"
    local summary_file="$3"
    local long_tail_run_id="$4"

    BASE_URL="$BASE_URL" \
    HOT_KEYWORDS="$HOT_KEYWORDS" \
    CAPACITY_SCENARIO="$scenario" \
    TARGET_RPS="$target_rps" \
    BASE_RPS="$BASE_RPS" \
    DURATION="$DURATION" \
    SPIKE_WARMUP_DURATION="$SPIKE_WARMUP_DURATION" \
    SPIKE_RAMP_DURATION="$SPIKE_RAMP_DURATION" \
    SPIKE_HOLD_DURATION="$SPIKE_HOLD_DURATION" \
    SPIKE_RECOVERY_DURATION="$SPIKE_RECOVERY_DURATION" \
    RAMP_DOWN_DURATION="$RAMP_DOWN_DURATION" \
    PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
    MAX_VUS="$MAX_VUS" \
    SEARCH_LIMIT="$SEARCH_LIMIT" \
    HOT_TRAFFIC_PERCENT="$HOT_TRAFFIC_PERCENT" \
    SPIKE_TRAFFIC_PROFILE="$SPIKE_TRAFFIC_PROFILE" \
    LONG_TAIL_PREFIX="$LONG_TAIL_PREFIX" \
    LONG_TAIL_RUN_ID="$long_tail_run_id" \
    LONG_TAIL_VARIANTS="$LONG_TAIL_VARIANTS" \
    P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
    FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
    ACCESS_TOKEN="$ACCESS_TOKEN" \
    k6 run \
        --summary-export "$ARTIFACT_DIR/$summary_file" \
        performance/k6/stress-es-cache-capacity-followup-200k.js
}

run_k6_docker() {
    local scenario="$1"
    local target_rps="$2"
    local summary_file="$3"
    local long_tail_run_id="$4"
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
        -e CAPACITY_SCENARIO="$scenario" \
        -e TARGET_RPS="$target_rps" \
        -e BASE_RPS="$BASE_RPS" \
        -e DURATION="$DURATION" \
        -e SPIKE_WARMUP_DURATION="$SPIKE_WARMUP_DURATION" \
        -e SPIKE_RAMP_DURATION="$SPIKE_RAMP_DURATION" \
        -e SPIKE_HOLD_DURATION="$SPIKE_HOLD_DURATION" \
        -e SPIKE_RECOVERY_DURATION="$SPIKE_RECOVERY_DURATION" \
        -e RAMP_DOWN_DURATION="$RAMP_DOWN_DURATION" \
        -e PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
        -e MAX_VUS="$MAX_VUS" \
        -e SEARCH_LIMIT="$SEARCH_LIMIT" \
        -e HOT_TRAFFIC_PERCENT="$HOT_TRAFFIC_PERCENT" \
        -e SPIKE_TRAFFIC_PROFILE="$SPIKE_TRAFFIC_PROFILE" \
        -e LONG_TAIL_PREFIX="$LONG_TAIL_PREFIX" \
        -e LONG_TAIL_RUN_ID="$long_tail_run_id" \
        -e LONG_TAIL_VARIANTS="$LONG_TAIL_VARIANTS" \
        -e P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
        -e FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
        -e ACCESS_TOKEN="$ACCESS_TOKEN" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$ARTIFACT_DIR:/k6-output" \
        grafana/k6 run \
            --summary-export "/k6-output/$summary_file" \
            /scripts/stress-es-cache-capacity-followup-200k.js
}

run_capacity_case() {
    local scenario="$1"
    local target_rps="$2"
    local long_tail_run_id="${RUN_LABEL}-${scenario}-${target_rps}-$(date +%Y%m%d%H%M%S)"
    local summary_file="${SUMMARY_PREFIX}_${scenario}_${target_rps}rps.json"
    local prom_before="${SUMMARY_PREFIX}_${scenario}_${target_rps}rps_prometheus_before.prom"
    local prom_after="${SUMMARY_PREFIX}_${scenario}_${target_rps}rps_prometheus_after.prom"

    echo
    echo "================================================================================"
    echo "### Capacity follow-up run: scenario=${scenario}, target=${target_rps} RPS"
    echo "================================================================================"
    echo "CAPACITY_SCENARIO=${scenario}"
    echo "TARGET_RPS=${target_rps}"
    echo "LONG_TAIL_RUN_ID=${long_tail_run_id}"

    reset_redis_cache

    if needs_hot_warmup "$scenario"; then
        local warmup_request_count=0
        IFS=',' read -r -a keyword_array <<<"$HOT_KEYWORDS"

        for ((round = 1; round <= WARMUP_ROUNDS; round++)); do
            for keyword in "${keyword_array[@]}"; do
                keyword="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$keyword")"
                [[ -z "$keyword" ]] && continue

                encoded_keyword="$(urlencode "$keyword")"
                warmup_url="${BASE_URL}/jobs/search?keyword=${encoded_keyword}&limit=${WARMUP_LIMIT}"

                if ! curl -fsS "${auth_header[@]}" "$warmup_url" >/dev/null 2>&1; then
                    echo "Cache warmup request failed: $warmup_url" >&2
                    return 1
                fi

                warmup_request_count=$((warmup_request_count + 1))
            done
        done

        echo "job_search_cache_warmup=ok scenario=${scenario} rounds=${WARMUP_ROUNDS} requests=${warmup_request_count}"
    else
        echo "job_search_cache_warmup=skipped scenario=${scenario}"
    fi

    local cache_hits_before cache_misses_before cache_pending_before
    cache_hits_before="$(cache_metric_total hit)"
    cache_misses_before="$(cache_metric_total miss)"
    cache_pending_before="$(cache_metric_total pending)"

    echo "prometheus_before_file=${ARTIFACT_DIR}/${prom_before}"
    prometheus_snapshot "${ARTIFACT_DIR}/${prom_before}"

    local k6_status=0
    set +e
    if command -v k6 >/dev/null 2>&1; then
        raise_nofile_limit
        run_k6_binary "$scenario" "$target_rps" "$summary_file" "$long_tail_run_id"
        k6_status=$?
    else
        run_k6_docker "$scenario" "$target_rps" "$summary_file" "$long_tail_run_id"
        k6_status=$?
    fi
    set -e

    local cache_hits_after cache_misses_after cache_pending_after
    cache_hits_after="$(cache_metric_total hit)"
    cache_misses_after="$(cache_metric_total miss)"
    cache_pending_after="$(cache_metric_total pending)"

    local cache_hits_delta=$((cache_hits_after - cache_hits_before))
    local cache_misses_delta=$((cache_misses_after - cache_misses_before))
    local cache_pending_delta=$((cache_pending_after - cache_pending_before))

    echo "prometheus_after_file=${ARTIFACT_DIR}/${prom_after}"
    prometheus_snapshot "${ARTIFACT_DIR}/${prom_after}"
    echo "capacity_followup_summary_export=${ARTIFACT_DIR}/${summary_file}"
    echo "job_search_cache_hits_before=$cache_hits_before"
    echo "job_search_cache_hits_after=$cache_hits_after"
    echo "job_search_cache_hits_delta=$cache_hits_delta"
    echo "job_search_cache_misses_before=$cache_misses_before"
    echo "job_search_cache_misses_after=$cache_misses_after"
    echo "job_search_cache_misses_delta=$cache_misses_delta"
    echo "job_search_cache_pending_before=$cache_pending_before"
    echo "job_search_cache_pending_after=$cache_pending_after"
    echo "job_search_cache_pending_delta=$cache_pending_delta"

    local validation_status=0
    validate_k6_summary "${ARTIFACT_DIR}/${summary_file}" "$target_rps" "$scenario" || validation_status=$?
    validate_cache_deltas "$scenario" "$cache_hits_delta" "$cache_misses_delta" || validation_status=$?

    if (( k6_status != 0 )); then
        echo "capacity_followup_run_failed scenario=${scenario} target_rps=${target_rps} exit_code=${k6_status}" >&2
        validation_status=$k6_status
    fi

    if (( validation_status != 0 && "$FAIL_FAST" == "true" )); then
        return "$validation_status"
    fi

    return 0
}

if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "true" ]]; then
    count="$(perf_fixture_count)"
    echo "perf_fixture_job_count=$count"
    if [[ "$count" != "$EXPECTED_PERF_JOB_COUNT" ]]; then
        echo "Expected ${EXPECTED_PERF_JOB_COUNT} perf_fixture jobs, got ${count}" >&2
        exit 1
    fi
else
    echo "perf_fixture_job_count=skipped reason=LOCAL_COMPOSE_PREFLIGHT_false"
fi

if [[ "$ES_COUNT_PREFLIGHT" == "true" ]]; then
    count="$(es_doc_count)"
    echo "es_performance_doc_count=$count"
    if [[ "$count" != "$EXPECTED_ES_DOC_COUNT" ]]; then
        echo "Expected ${EXPECTED_ES_DOC_COUNT} Elasticsearch docs in ${ELASTICSEARCH_JOBS_ALIAS}, got ${count}" >&2
        exit 1
    fi
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
    echo "Prometheus endpoint preflight failed: $PROMETHEUS_URL" >&2
    exit 1
fi
echo "prometheus_preflight=ok"

if [[ "$REQUIRE_BACKEND_CACHE_ENABLED" == "true" ]]; then
    cache_enabled="$(backend_env_value CACHE_ENABLED)"
    if [[ "$LOCAL_COMPOSE_PREFLIGHT" == "true" ]]; then
        echo "backend_CACHE_ENABLED=$cache_enabled"
        if [[ "$cache_enabled" != "true" ]]; then
            echo "CACHE_ENABLED must be true for cache capacity follow-up tests." >&2
            exit 1
        fi
    else
        echo "backend_cache_enabled=not_checked_directly reason=LOCAL_COMPOSE_PREFLIGHT_false"
        echo "backend_cache_enabled_validation=cache_delta_per_scenario"
    fi
else
    echo "backend_cache_enabled=skipped"
fi

case "$SEARCH_AUTH_MODE" in
    public)
        if [[ -n "$ACCESS_TOKEN" ]]; then
            echo "SEARCH_AUTH_MODE=public ignores provided ACCESS_TOKEN so public /jobs/search capacity does not include JWT DB lookup cost."
        fi
        ACCESS_TOKEN=""
        echo "auth_preflight=skipped mode=public"
        ;;
    login)
        if [[ -z "$ACCESS_TOKEN" ]]; then
            if login_body="$(curl -fsS -X POST "${BASE_URL}/auth/login" \
                -H 'Content-Type: application/json' \
                -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" 2>/dev/null)"; then
                ACCESS_TOKEN="$(jq -r '.data.accessToken // empty' <<<"$login_body")"
                if [[ -z "$ACCESS_TOKEN" ]]; then
                    echo "Auth preflight failed: login succeeded but no accessToken in response." >&2
                    echo "$login_body" >&2
                    exit 1
                fi
            else
                echo "Auth preflight failed: login failed for SEARCH_AUTH_MODE=login." >&2
                exit 1
            fi
        fi
        echo "auth_preflight=ok mode=login"
        ;;
    token)
        if [[ -z "$ACCESS_TOKEN" ]]; then
            echo "Auth preflight failed: SEARCH_AUTH_MODE=token requires ACCESS_TOKEN." >&2
            exit 1
        fi
        echo "auth_preflight=ok mode=token"
        ;;
    *)
        echo "Invalid SEARCH_AUTH_MODE: $SEARCH_AUTH_MODE (expected public, login, or token)" >&2
        exit 1
        ;;
esac

auth_header=()
if [[ -n "$ACCESS_TOKEN" ]]; then
    auth_header=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
fi

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

IFS=',' read -r -a scenario_array <<<"$CAPACITY_SCENARIOS"
IFS=',' read -r -a target_rps_array <<<"$TARGET_RPS_LIST"

case_count=0
for scenario in "${scenario_array[@]}"; do
    scenario="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$scenario")"
    [[ -z "$scenario" ]] && continue
    for target_rps in "${target_rps_array[@]}"; do
        target_rps="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$target_rps")"
        [[ -z "$target_rps" ]] && continue
        case_count=$((case_count + 1))
    done
done

if (( case_count > 1 )) && [[ "$RESET_REDIS_CACHE_BEFORE_RUN" != "true" && "$ALLOW_MULTI_CASE_WITHOUT_RESET" != "true" ]]; then
    echo "Multiple capacity cases require Redis reset between runs." >&2
    echo "Run one scenario/RPS at a time after manually flushing Redis, or set ALLOW_MULTI_CASE_WITHOUT_RESET=true only for intentional warmed-cache experiments." >&2
    exit 1
fi

for scenario in "${scenario_array[@]}"; do
    scenario="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$scenario")"
    [[ -z "$scenario" ]] && continue
    if [[ "$scenario" != "hot" && "$scenario" != "cold" && "$scenario" != "mixed" && "$scenario" != "spike" ]]; then
        echo "Invalid CAPACITY_SCENARIO value: $scenario" >&2
        exit 1
    fi

    for target_rps in "${target_rps_array[@]}"; do
        target_rps="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$target_rps")"
        [[ -z "$target_rps" ]] && continue
        if ! [[ "$target_rps" =~ ^[0-9]+$ ]]; then
            echo "Invalid TARGET_RPS value: $target_rps" >&2
            exit 1
        fi

        run_capacity_case "$scenario" "$target_rps"
    done
done

echo
echo "Capacity follow-up stress test completed."
echo "summary_prefix=$ARTIFACT_DIR/$SUMMARY_PREFIX"
