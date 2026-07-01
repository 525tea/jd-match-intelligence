#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SERVER_URL="${SERVER_URL:-${BASE_URL%/api}}"
HEALTH_URL="${HEALTH_URL:-${SERVER_URL}/actuator/health/liveness}"
PROMETHEUS_URL="${PROMETHEUS_URL:-${SERVER_URL}/actuator/prometheus}"
ENDPOINTS="${ENDPOINTS:-gap_analysis,jd_match,recommendations_jobs}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,DATA_ENGINEER,DEVOPS}"
TARGET_CAREER_LEVELS="${TARGET_CAREER_LEVELS:-JUNIOR,MID,SENIOR}"
VUS="${VUS:-200}"
DURATION="${DURATION:-10m}"
LIMIT="${LIMIT:-20}"
LIMIT_VALUES="${LIMIT_VALUES:-$LIMIT}"
TARGET_RPS="${TARGET_RPS:-}"
PRE_ALLOCATED_VUS="${PRE_ALLOCATED_VUS:-800}"
MAX_VUS="${MAX_VUS:-4000}"
SLEEP_SECONDS="${SLEEP_SECONDS:-}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-5000}"
FAIL_RATE_THRESHOLD="${FAIL_RATE_THRESHOLD:-0.02}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
CACHE_MODE="${CACHE_MODE:-enabled}"
WORKLOAD_MODE="${WORKLOAD_MODE:-hot}"
HOT_RATIO="${HOT_RATIO:-0.7}"
HOT_VARIANTS="${HOT_VARIANTS:-9}"
LONG_TAIL_VARIANTS="${LONG_TAIL_VARIANTS:-100000}"
ROLE_COMBINATION_SIZE="${ROLE_COMBINATION_SIZE:-1}"
RUN_LABEL="${RUN_LABEL:-analysis_cache}"
RUN_LOCATION="${RUN_LOCATION:-internal}"
SUMMARY_PREFIX="${SUMMARY_PREFIX:-$(date +%y%m%d)_k6_analysis_cache_${CACHE_MODE}_${WORKLOAD_MODE}_${RUN_LABEL}_${RUN_LOCATION}}"
if [[ -n "$TARGET_RPS" ]]; then
    SUMMARY_FILE="${SUMMARY_FILE:-${SUMMARY_PREFIX}_${TARGET_RPS}rps.json}"
else
    SUMMARY_FILE="${SUMMARY_FILE:-${SUMMARY_PREFIX}_${VUS}vu.json}"
fi
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
USER_PROJECT_IDS="${USER_PROJECT_IDS:-$USER_PROJECT_ID}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
REQUIRE_BACKEND_CACHE_ENABLED="${REQUIRE_BACKEND_CACHE_ENABLED:-true}"
RESET_REDIS_CACHE_BEFORE_RUN="${RESET_REDIS_CACHE_BEFORE_RUN:-true}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-2}"
WARMUP_VARIANTS="${WARMUP_VARIANTS:-$HOT_VARIANTS}"
MIN_ANALYSIS_CACHE_HIT_DELTA="${MIN_ANALYSIS_CACHE_HIT_DELTA:-3}"
MIN_COLD_CACHE_MISS_RATIO="${MIN_COLD_CACHE_MISS_RATIO:-0.95}"
FAILURE_SAMPLE_BODY_LIMIT="${FAILURE_SAMPLE_BODY_LIMIT:-500}"
K6_SCRIPT="${K6_SCRIPT:-performance/k6/stress-analysis-cache.js}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

mkdir -p "$ARTIFACT_DIR"
if ! command -v k6 >/dev/null 2>&1; then
    chmod 777 "$ARTIFACT_DIR"
fi
ARTIFACT_DIR="$(cd "$ARTIFACT_DIR" && pwd)"

ANALYSIS_CACHES=(gapAnalysis jdMatch jobRecommendation)

echo "BASE_URL=$BASE_URL"
echo "HEALTH_URL=$HEALTH_URL"
echo "PROMETHEUS_URL=$PROMETHEUS_URL"
echo "TARGET=backend-direct"
echo "ENDPOINTS=$ENDPOINTS"
echo "TARGET_ROLES=$TARGET_ROLES"
echo "TARGET_CAREER_LEVELS=$TARGET_CAREER_LEVELS"
echo "VUS=$VUS"
echo "DURATION=$DURATION"
echo "LIMIT=$LIMIT"
echo "LIMIT_VALUES=$LIMIT_VALUES"
echo "TARGET_RPS=${TARGET_RPS:-empty}"
echo "PRE_ALLOCATED_VUS=$PRE_ALLOCATED_VUS"
echo "MAX_VUS=$MAX_VUS"
echo "SLEEP_SECONDS=${SLEEP_SECONDS:-default}"
echo "P95_THRESHOLD_MS=$P95_THRESHOLD_MS"
echo "FAIL_RATE_THRESHOLD=$FAIL_RATE_THRESHOLD"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "CACHE_MODE=$CACHE_MODE"
echo "WORKLOAD_MODE=$WORKLOAD_MODE"
echo "HOT_RATIO=$HOT_RATIO"
echo "HOT_VARIANTS=$HOT_VARIANTS"
echo "LONG_TAIL_VARIANTS=$LONG_TAIL_VARIANTS"
echo "ROLE_COMBINATION_SIZE=$ROLE_COMBINATION_SIZE"
echo "RUN_LABEL=$RUN_LABEL"
echo "RUN_LOCATION=$RUN_LOCATION"
echo "SUMMARY_FILE=$SUMMARY_FILE"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "USER_PROJECT_ID=${USER_PROJECT_ID:-empty}"
echo "USER_PROJECT_IDS=$([ -n "$USER_PROJECT_IDS" ] && echo provided || echo empty)"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "REQUIRE_BACKEND_CACHE_ENABLED=$REQUIRE_BACKEND_CACHE_ENABLED"
echo "RESET_REDIS_CACHE_BEFORE_RUN=$RESET_REDIS_CACHE_BEFORE_RUN"
echo "WARMUP_ROUNDS=$WARMUP_ROUNDS"
echo "WARMUP_VARIANTS=$WARMUP_VARIANTS"
echo "MIN_ANALYSIS_CACHE_HIT_DELTA=$MIN_ANALYSIS_CACHE_HIT_DELTA"
echo "MIN_COLD_CACHE_MISS_RATIO=$MIN_COLD_CACHE_MISS_RATIO"
echo "FAILURE_SAMPLE_BODY_LIMIT=$FAILURE_SAMPLE_BODY_LIMIT"

csv_count() {
    local value="$1"
    if [[ -z "$value" ]]; then
        printf "0"
        return
    fi

    awk -F',' '{ print NF }' <<<"$value"
}

urlencode() {
    jq -rn --arg value "$1" '$value|@uri'
}

backend_env_value() {
    local name="$1"
    docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T backend \
        sh -lc "printenv ${name} || true" 2>/dev/null | tr -d '\r'
}

cache_metric_total() {
    local cache_name="$1"
    local result="$2"
    curl -fsS "$PROMETHEUS_URL" 2>/dev/null | awk -v cache_name="$cache_name" -v result="$result" '
        $0 !~ /^#/ &&
        $0 ~ /^cache_gets_total/ &&
        index($0, "result=\"" result "\"") > 0 &&
        (index($0, "cache=\"" cache_name "\"") > 0 || index($0, "name=\"" cache_name "\"") > 0) {
            total += $NF
        }
        END {
            printf "%.0f", total + 0
        }
    '
}

cache_metric_sum() {
    local result="$1"
    local total=0
    local cache_name value

    for cache_name in "${ANALYSIS_CACHES[@]}"; do
        value="$(cache_metric_total "$cache_name" "$result")"
        total=$((total + value))
    done

    printf "%d" "$total"
}

reset_redis_cache() {
    if [[ "$RESET_REDIS_CACHE_BEFORE_RUN" != "true" ]]; then
        echo "redis_cache_reset=skipped"
        return
    fi

    if ! docker compose -f docker-compose.yml -f docker-compose.performance.yml ps --services --filter status=running \
        | grep -qx redis; then
        echo "Redis cache reset failed: redis service is not running." >&2
        exit 1
    fi

    docker compose -f docker-compose.yml -f docker-compose.performance.yml exec -T redis \
        redis-cli FLUSHDB >/dev/null

    echo "redis_cache_reset=ok"
}

login() {
    if [[ -n "$ACCESS_TOKEN" ]]; then
        return
    fi

    local login_body login_payload
    login_payload="$(jq -nc \
        --arg email "$LOGIN_EMAIL" \
        --arg password "$LOGIN_PASSWORD" \
        '{email:$email,password:$password}')"
    if ! login_body="$(curl -fsS -X POST "${BASE_URL}/auth/login" \
        -H 'Content-Type: application/json' \
        -d "$login_payload" 2>/dev/null)"; then
        echo "Auth preflight failed: login failed." >&2
        exit 1
    fi

    ACCESS_TOKEN="$(jq -r '.data.accessToken // empty' <<<"$login_body")"
    if [[ -z "$ACCESS_TOKEN" ]]; then
        echo "Auth preflight failed: login succeeded but no accessToken in response." >&2
        echo "$login_body" >&2
        exit 1
    fi
}

resolve_user_project_id() {
    if [[ -n "$USER_PROJECT_ID" ]]; then
        if [[ -z "$USER_PROJECT_IDS" ]]; then
            USER_PROJECT_IDS="$USER_PROJECT_ID"
        fi
        return
    fi

    if [[ -n "$USER_PROJECT_IDS" ]]; then
        IFS=',' read -r USER_PROJECT_ID _ <<<"$USER_PROJECT_IDS"
        USER_PROJECT_ID="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$USER_PROJECT_ID")"
        return
    fi

    local me_body
    if ! me_body="$(curl -fsS "${BASE_URL}/auth/me" \
        -H "Authorization: Bearer ${ACCESS_TOKEN}" 2>/dev/null)"; then
        echo "Auth preflight failed: /auth/me failed." >&2
        exit 1
    fi

    USER_PROJECT_ID="$(jq -r '.data.userProjectId // empty' <<<"$me_body")"
    if [[ -z "$USER_PROJECT_ID" || "$USER_PROJECT_ID" == "null" ]]; then
        echo "Auth preflight failed: /auth/me did not return userProjectId." >&2
        echo "$me_body" >&2
        exit 1
    fi

    USER_PROJECT_IDS="$USER_PROJECT_ID"
}

target_roles_query() {
    local index="$1"
    IFS=',' read -r -a roles <<<"$TARGET_ROLES"
    local role_count="${#roles[@]}"
    local combo_size="$ROLE_COMBINATION_SIZE"
    local offset role encoded_role query=""

    if ((combo_size < 1)); then
        combo_size=1
    fi
    if ((combo_size > role_count)); then
        combo_size="$role_count"
    fi

    for ((offset = 0; offset < combo_size; offset++)); do
        role="${roles[$(((index + offset) % role_count))]}"
        role="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$role")"
        encoded_role="$(urlencode "$role")"
        if [[ -n "$query" ]]; then
            query="${query}&"
        fi
        query="${query}targetRoles=${encoded_role}"
    done

    printf "%s" "$query"
}

target_career_level() {
    local index="$1"
    IFS=',' read -r -a career_levels <<<"$TARGET_CAREER_LEVELS"
    local career_level_count="${#career_levels[@]}"
    local career_level="${career_levels[$((index % career_level_count))]}"
    sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$career_level"
}

target_limit() {
    local index="$1"
    IFS=',' read -r -a limits <<<"$LIMIT_VALUES"
    local limit_count="${#limits[@]}"
    local limit="${limits[$((index % limit_count))]}"
    sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$limit"
}

analysis_url() {
    local endpoint="$1"
    local index="$2"
    local career_level limit encoded_project_id encoded_career_level roles_query

    career_level="$(target_career_level "$index")"
    limit="$(target_limit "$index")"
    encoded_project_id="$(urlencode "$USER_PROJECT_ID")"
    encoded_career_level="$(urlencode "$career_level")"
    roles_query="$(target_roles_query "$index")"

    case "$endpoint" in
        gap_analysis)
            printf "%s/gap-analysis/projects/%s?%s&limit=%s" \
                "$BASE_URL" "$encoded_project_id" "$roles_query" "$limit"
            ;;
        jd_match)
            printf "%s/projects/%s/job-matches?%s&targetCareerLevel=%s&limit=%s" \
                "$BASE_URL" "$encoded_project_id" "$roles_query" "$encoded_career_level" "$limit"
            ;;
        recommendations_jobs)
            printf "%s/recommendations/jobs?userProjectId=%s&%s&limit=%s" \
                "$BASE_URL" "$encoded_project_id" "$roles_query" "$limit"
            ;;
        *)
            echo "Unsupported endpoint in ENDPOINTS: $endpoint" >&2
            exit 1
            ;;
    esac
}

request_endpoint() {
    local endpoint="$1"
    local index="$2"
    local url response

    url="$(analysis_url "$endpoint" "$index")"
    if ! response="$(curl -fsS "$url" -H "Authorization: Bearer ${ACCESS_TOKEN}" 2>/dev/null)"; then
        echo "Analysis API preflight failed: endpoint=$endpoint url=$url" >&2
        exit 1
    fi

    if ! grep -Eq '"success"[[:space:]]*:[[:space:]]*true' <<<"$response"; then
        echo "Analysis API returned unexpected response: endpoint=$endpoint url=$url" >&2
        echo "$response" >&2
        exit 1
    fi
}

if [[ "$CACHE_MODE" != "enabled" && "$CACHE_MODE" != "disabled" ]]; then
    echo "CACHE_MODE must be enabled or disabled." >&2
    exit 1
fi

if [[ "$WORKLOAD_MODE" != "hot" && "$WORKLOAD_MODE" != "mixed" && "$WORKLOAD_MODE" != "cold" ]]; then
    echo "WORKLOAD_MODE must be hot, mixed, or cold." >&2
    exit 1
fi

if [[ ! "$ROLE_COMBINATION_SIZE" =~ ^[0-9]+$ || "$ROLE_COMBINATION_SIZE" -lt 1 ]]; then
    echo "ROLE_COMBINATION_SIZE must be a positive integer." >&2
    exit 1
fi

if [[ ! "$HOT_VARIANTS" =~ ^[0-9]+$ || "$HOT_VARIANTS" -lt 1 ]]; then
    echo "HOT_VARIANTS must be a positive integer." >&2
    exit 1
fi

if [[ ! "$LONG_TAIL_VARIANTS" =~ ^[0-9]+$ || "$LONG_TAIL_VARIANTS" -lt 1 ]]; then
    echo "LONG_TAIL_VARIANTS must be a positive integer." >&2
    exit 1
fi

if [[ ! "$WARMUP_VARIANTS" =~ ^[0-9]+$ || "$WARMUP_VARIANTS" -lt 1 ]]; then
    echo "WARMUP_VARIANTS must be a positive integer." >&2
    exit 1
fi

if ! awk "BEGIN { exit !($MIN_COLD_CACHE_MISS_RATIO >= 0 && $MIN_COLD_CACHE_MISS_RATIO <= 1) }"; then
    echo "MIN_COLD_CACHE_MISS_RATIO must be between 0 and 1." >&2
    exit 1
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
    exit 1
fi

echo "prometheus_preflight=ok"

cache_enabled_value="skipped"
perf_cache_enabled_value="skipped"

if [[ "$REQUIRE_BACKEND_CACHE_ENABLED" == "true" ]]; then
    cache_enabled_value="$(backend_env_value CACHE_ENABLED)"
    perf_cache_enabled_value="$(backend_env_value PERF_CACHE_ENABLED)"

    if [[ "$CACHE_MODE" == "enabled" && "$cache_enabled_value" != "true" && "$perf_cache_enabled_value" != "true" ]]; then
        echo "Backend cache preflight failed: expected CACHE_ENABLED=true or PERF_CACHE_ENABLED=true." >&2
        echo "Current values: CACHE_ENABLED=${cache_enabled_value:-empty}, PERF_CACHE_ENABLED=${perf_cache_enabled_value:-empty}" >&2
        exit 1
    fi

    if [[ "$CACHE_MODE" == "disabled" && "$cache_enabled_value" != "false" && "$perf_cache_enabled_value" != "false" ]]; then
        echo "Backend cache preflight failed: expected CACHE_ENABLED=false or PERF_CACHE_ENABLED=false for before run." >&2
        echo "Current values: CACHE_ENABLED=${cache_enabled_value:-empty}, PERF_CACHE_ENABLED=${perf_cache_enabled_value:-empty}" >&2
        exit 1
    fi
fi

echo "backend_cache_env=CACHE_ENABLED=${cache_enabled_value:-empty},PERF_CACHE_ENABLED=${perf_cache_enabled_value:-empty}"

login
resolve_user_project_id

echo "auth_preflight=ok"
echo "resolved_user_project_id=$USER_PROJECT_ID"
echo "resolved_user_project_ids_count=$(csv_count "$USER_PROJECT_IDS")"

reset_redis_cache

analysis_hits_before="$(cache_metric_sum hit)"
analysis_misses_before="$(cache_metric_sum miss)"

IFS=',' read -r -a endpoint_array <<<"$ENDPOINTS"
preflight_index=0
for endpoint in "${endpoint_array[@]}"; do
    endpoint="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$endpoint")"
    [[ -z "$endpoint" ]] && continue
    request_endpoint "$endpoint" "$preflight_index"
    preflight_index=$((preflight_index + 1))
done

echo "analysis_api_preflight=ok endpoints=$ENDPOINTS"

if [[ "$CACHE_MODE" == "enabled" ]]; then
    warmup_count=0
    for ((round = 1; round <= WARMUP_ROUNDS; round++)); do
        for ((variant = 0; variant < WARMUP_VARIANTS; variant++)); do
            for endpoint in "${endpoint_array[@]}"; do
                endpoint="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$endpoint")"
                [[ -z "$endpoint" ]] && continue
                request_endpoint "$endpoint" "$variant"
                warmup_count=$((warmup_count + 1))
            done
        done
    done
    echo "analysis_cache_warmup=ok rounds=${WARMUP_ROUNDS} variants=${WARMUP_VARIANTS} requests=${warmup_count}"
else
    echo "analysis_cache_warmup=skipped"
fi

analysis_hits_after="$(cache_metric_sum hit)"
analysis_misses_after="$(cache_metric_sum miss)"
analysis_hits_delta=$((analysis_hits_after - analysis_hits_before))
analysis_misses_delta=$((analysis_misses_after - analysis_misses_before))

echo "analysis_cache_hits_before=$analysis_hits_before"
echo "analysis_cache_hits_after=$analysis_hits_after"
echo "analysis_cache_hits_delta=$analysis_hits_delta"
echo "analysis_cache_misses_before=$analysis_misses_before"
echo "analysis_cache_misses_after=$analysis_misses_after"
echo "analysis_cache_misses_delta=$analysis_misses_delta"

if [[ "$CACHE_MODE" == "enabled" && "$analysis_hits_delta" -lt "$MIN_ANALYSIS_CACHE_HIT_DELTA" ]]; then
    echo "Analysis cache preflight failed: expected at least ${MIN_ANALYSIS_CACHE_HIT_DELTA} cache hits after warmup." >&2
    exit 1
fi

echo "analysis_cache_preflight=ok"

summary_path="${ARTIFACT_DIR}/${SUMMARY_FILE}"

if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    ACCESS_TOKEN="$ACCESS_TOKEN" \
    USER_PROJECT_ID="$USER_PROJECT_ID" \
    USER_PROJECT_IDS="$USER_PROJECT_IDS" \
    LOGIN_EMAIL="$LOGIN_EMAIL" \
    LOGIN_PASSWORD="$LOGIN_PASSWORD" \
    ENDPOINTS="$ENDPOINTS" \
    TARGET_ROLES="$TARGET_ROLES" \
    TARGET_CAREER_LEVELS="$TARGET_CAREER_LEVELS" \
    VUS="$VUS" \
    DURATION="$DURATION" \
    LIMIT="$LIMIT" \
    LIMIT_VALUES="$LIMIT_VALUES" \
    TARGET_RPS="$TARGET_RPS" \
    PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
    MAX_VUS="$MAX_VUS" \
    SLEEP_SECONDS="${SLEEP_SECONDS:-}" \
    P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
    FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
    FAILURE_SAMPLE_BODY_LIMIT="$FAILURE_SAMPLE_BODY_LIMIT" \
    CACHE_MODE="$CACHE_MODE" \
    WORKLOAD_MODE="$WORKLOAD_MODE" \
    HOT_RATIO="$HOT_RATIO" \
    HOT_VARIANTS="$HOT_VARIANTS" \
    LONG_TAIL_VARIANTS="$LONG_TAIL_VARIANTS" \
    ROLE_COMBINATION_SIZE="$ROLE_COMBINATION_SIZE" \
    k6 run --summary-export "$summary_path" "$K6_SCRIPT"
else
    docker run --rm --network host \
        -e BASE_URL="$BASE_URL" \
        -e ACCESS_TOKEN="$ACCESS_TOKEN" \
        -e USER_PROJECT_ID="$USER_PROJECT_ID" \
        -e USER_PROJECT_IDS="$USER_PROJECT_IDS" \
        -e LOGIN_EMAIL="$LOGIN_EMAIL" \
        -e LOGIN_PASSWORD="$LOGIN_PASSWORD" \
        -e ENDPOINTS="$ENDPOINTS" \
        -e TARGET_ROLES="$TARGET_ROLES" \
        -e TARGET_CAREER_LEVELS="$TARGET_CAREER_LEVELS" \
        -e VUS="$VUS" \
        -e DURATION="$DURATION" \
        -e LIMIT="$LIMIT" \
        -e LIMIT_VALUES="$LIMIT_VALUES" \
        -e TARGET_RPS="$TARGET_RPS" \
        -e PRE_ALLOCATED_VUS="$PRE_ALLOCATED_VUS" \
        -e MAX_VUS="$MAX_VUS" \
        -e SLEEP_SECONDS="${SLEEP_SECONDS:-}" \
        -e P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
        -e FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
        -e FAILURE_SAMPLE_BODY_LIMIT="$FAILURE_SAMPLE_BODY_LIMIT" \
        -e CACHE_MODE="$CACHE_MODE" \
        -e WORKLOAD_MODE="$WORKLOAD_MODE" \
        -e HOT_RATIO="$HOT_RATIO" \
        -e HOT_VARIANTS="$HOT_VARIANTS" \
        -e LONG_TAIL_VARIANTS="$LONG_TAIL_VARIANTS" \
        -e ROLE_COMBINATION_SIZE="$ROLE_COMBINATION_SIZE" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$ARTIFACT_DIR:/k6-output" \
        grafana/k6 run --summary-export "/k6-output/$SUMMARY_FILE" "/scripts/$(basename "$K6_SCRIPT")"
fi

analysis_hits_final="$(cache_metric_sum hit)"
analysis_misses_final="$(cache_metric_sum miss)"
analysis_hits_run_delta=$((analysis_hits_final - analysis_hits_after))
analysis_misses_run_delta=$((analysis_misses_final - analysis_misses_after))
summary_http_reqs="$(jq -r '.metrics.http_reqs.count // .metrics.http_reqs.value // 0' "$summary_path")"
summary_http_reqs="${summary_http_reqs%.*}"
if [[ -z "$summary_http_reqs" || ! "$summary_http_reqs" =~ ^[0-9]+$ ]]; then
    summary_http_reqs=0
fi

echo
echo "Analysis cache stress test completed."
echo "summary_export=$summary_path"
echo "analysis_cache_hits_final=$analysis_hits_final"
echo "analysis_cache_hits_run_delta=$analysis_hits_run_delta"
echo "analysis_cache_misses_final=$analysis_misses_final"
echo "analysis_cache_misses_run_delta=$analysis_misses_run_delta"
echo "summary_http_reqs=$summary_http_reqs"

if [[ "$CACHE_MODE" == "enabled" && "$WORKLOAD_MODE" == "cold" ]]; then
    if ((summary_http_reqs == 0)); then
        echo "Cold cache validation failed: summary_http_reqs is 0." >&2
        exit 1
    fi

    cold_miss_ratio="$(awk -v misses="$analysis_misses_run_delta" -v requests="$summary_http_reqs" 'BEGIN { printf "%.6f", misses / requests }')"
    echo "analysis_cache_cold_miss_ratio=$cold_miss_ratio"

    if ! awk -v ratio="$cold_miss_ratio" -v min="$MIN_COLD_CACHE_MISS_RATIO" 'BEGIN { exit !(ratio >= min) }'; then
        echo "Cold cache validation failed: expected miss ratio >= ${MIN_COLD_CACHE_MISS_RATIO}, got ${cold_miss_ratio}." >&2
        echo "This run is not a pure miss boundary. Increase USER_PROJECT_IDS, ROLE_COMBINATION_SIZE, TARGET_ROLES, LIMIT_VALUES, or lower TARGET_RPS/DURATION." >&2
        exit 1
    fi

    echo "analysis_cache_cold_validation=ok"
fi
