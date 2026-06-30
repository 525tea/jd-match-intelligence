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
SLEEP_SECONDS="${SLEEP_SECONDS:-1}"
P95_THRESHOLD_MS="${P95_THRESHOLD_MS:-5000}"
FAIL_RATE_THRESHOLD="${FAIL_RATE_THRESHOLD:-0.02}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/performance}"
CACHE_MODE="${CACHE_MODE:-enabled}"
SUMMARY_FILE="${SUMMARY_FILE:-$(date +%y%m%d)_k6_analysis_cache_${CACHE_MODE}_200vu.json}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
USER_PROJECT_ID="${USER_PROJECT_ID:-}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
REQUIRE_BACKEND_CACHE_ENABLED="${REQUIRE_BACKEND_CACHE_ENABLED:-true}"
RESET_REDIS_CACHE_BEFORE_RUN="${RESET_REDIS_CACHE_BEFORE_RUN:-true}"
WARMUP_ROUNDS="${WARMUP_ROUNDS:-2}"
MIN_ANALYSIS_CACHE_HIT_DELTA="${MIN_ANALYSIS_CACHE_HIT_DELTA:-3}"
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
echo "SLEEP_SECONDS=$SLEEP_SECONDS"
echo "P95_THRESHOLD_MS=$P95_THRESHOLD_MS"
echo "FAIL_RATE_THRESHOLD=$FAIL_RATE_THRESHOLD"
echo "ARTIFACT_DIR=$ARTIFACT_DIR"
echo "CACHE_MODE=$CACHE_MODE"
echo "SUMMARY_FILE=$SUMMARY_FILE"
echo "ACCESS_TOKEN=$([ -n "$ACCESS_TOKEN" ] && echo provided || echo empty)"
echo "USER_PROJECT_ID=${USER_PROJECT_ID:-empty}"
echo "LOGIN_EMAIL=$([ -n "$LOGIN_EMAIL" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "$LOGIN_PASSWORD" ] && echo provided || echo empty)"
echo "REQUIRE_BACKEND_CACHE_ENABLED=$REQUIRE_BACKEND_CACHE_ENABLED"
echo "RESET_REDIS_CACHE_BEFORE_RUN=$RESET_REDIS_CACHE_BEFORE_RUN"
echo "WARMUP_ROUNDS=$WARMUP_ROUNDS"
echo "MIN_ANALYSIS_CACHE_HIT_DELTA=$MIN_ANALYSIS_CACHE_HIT_DELTA"
echo "FAILURE_SAMPLE_BODY_LIMIT=$FAILURE_SAMPLE_BODY_LIMIT"

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
}

target_role() {
    local index="$1"
    IFS=',' read -r -a roles <<<"$TARGET_ROLES"
    local role_count="${#roles[@]}"
    local role="${roles[$((index % role_count))]}"
    sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$role"
}

target_career_level() {
    local index="$1"
    IFS=',' read -r -a career_levels <<<"$TARGET_CAREER_LEVELS"
    local career_level_count="${#career_levels[@]}"
    local career_level="${career_levels[$((index % career_level_count))]}"
    sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$career_level"
}

analysis_url() {
    local endpoint="$1"
    local index="$2"
    local role career_level encoded_project_id encoded_role encoded_career_level

    role="$(target_role "$index")"
    career_level="$(target_career_level "$index")"
    encoded_project_id="$(urlencode "$USER_PROJECT_ID")"
    encoded_role="$(urlencode "$role")"
    encoded_career_level="$(urlencode "$career_level")"

    case "$endpoint" in
        gap_analysis)
            printf "%s/gap-analysis/projects/%s?targetRoles=%s&limit=%s" \
                "$BASE_URL" "$encoded_project_id" "$encoded_role" "$LIMIT"
            ;;
        jd_match)
            printf "%s/projects/%s/job-matches?targetRoles=%s&targetCareerLevel=%s&limit=%s" \
                "$BASE_URL" "$encoded_project_id" "$encoded_role" "$encoded_career_level" "$LIMIT"
            ;;
        recommendations_jobs)
            printf "%s/recommendations/jobs?userProjectId=%s&targetRoles=%s&limit=%s" \
                "$BASE_URL" "$encoded_project_id" "$encoded_role" "$LIMIT"
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

cache_enabled_value="$(backend_env_value CACHE_ENABLED)"
perf_cache_enabled_value="$(backend_env_value PERF_CACHE_ENABLED)"

if [[ "$REQUIRE_BACKEND_CACHE_ENABLED" == "true" ]]; then
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
        for endpoint in "${endpoint_array[@]}"; do
            endpoint="$(sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' <<<"$endpoint")"
            [[ -z "$endpoint" ]] && continue
            request_endpoint "$endpoint" "$warmup_count"
            warmup_count=$((warmup_count + 1))
        done
    done
    echo "analysis_cache_warmup=ok rounds=${WARMUP_ROUNDS} requests=${warmup_count}"
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
    LOGIN_EMAIL="$LOGIN_EMAIL" \
    LOGIN_PASSWORD="$LOGIN_PASSWORD" \
    ENDPOINTS="$ENDPOINTS" \
    TARGET_ROLES="$TARGET_ROLES" \
    TARGET_CAREER_LEVELS="$TARGET_CAREER_LEVELS" \
    VUS="$VUS" \
    DURATION="$DURATION" \
    LIMIT="$LIMIT" \
    SLEEP_SECONDS="$SLEEP_SECONDS" \
    P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
    FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
    FAILURE_SAMPLE_BODY_LIMIT="$FAILURE_SAMPLE_BODY_LIMIT" \
    CACHE_MODE="$CACHE_MODE" \
    k6 run --summary-export "$summary_path" "$K6_SCRIPT"
else
    docker run --rm --network host \
        -e BASE_URL="$BASE_URL" \
        -e ACCESS_TOKEN="$ACCESS_TOKEN" \
        -e USER_PROJECT_ID="$USER_PROJECT_ID" \
        -e LOGIN_EMAIL="$LOGIN_EMAIL" \
        -e LOGIN_PASSWORD="$LOGIN_PASSWORD" \
        -e ENDPOINTS="$ENDPOINTS" \
        -e TARGET_ROLES="$TARGET_ROLES" \
        -e TARGET_CAREER_LEVELS="$TARGET_CAREER_LEVELS" \
        -e VUS="$VUS" \
        -e DURATION="$DURATION" \
        -e LIMIT="$LIMIT" \
        -e SLEEP_SECONDS="$SLEEP_SECONDS" \
        -e P95_THRESHOLD_MS="$P95_THRESHOLD_MS" \
        -e FAIL_RATE_THRESHOLD="$FAIL_RATE_THRESHOLD" \
        -e FAILURE_SAMPLE_BODY_LIMIT="$FAILURE_SAMPLE_BODY_LIMIT" \
        -e CACHE_MODE="$CACHE_MODE" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$ARTIFACT_DIR:/k6-output" \
        grafana/k6 run --summary-export "/k6-output/$SUMMARY_FILE" "/scripts/$(basename "$K6_SCRIPT")"
fi

echo
echo "Analysis cache stress test completed."
echo "summary_export=$summary_path"
