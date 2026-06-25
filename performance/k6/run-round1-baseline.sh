#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
VUS="${VUS:-20}"
DURATION="${DURATION:-10m}"
KEYWORDS="${KEYWORDS:-performance,backend,data,devops,security}"
PAGE_SIZE="${PAGE_SIZE:-20}"
SEARCH_LIMIT="${SEARCH_LIMIT:-10}"
RECOMMENDATION_LIMIT="${RECOMMENDATION_LIMIT:-10}"
GAP_LIMIT="${GAP_LIMIT:-10}"
SLEEP_SECONDS="${SLEEP_SECONDS:-1}"
EXPECT_PERF_FIXTURE="${EXPECT_PERF_FIXTURE:-true}"
REQUIRE_AUTH_ENDPOINTS="${REQUIRE_AUTH_ENDPOINTS:-true}"
FAILURE_SAMPLE_BODY_LIMIT="${FAILURE_SAMPLE_BODY_LIMIT:-500}"
K6_SCRIPT="${K6_SCRIPT:-performance/k6/round1-monolith-baseline.js}"
K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT:-/tmp/jobflow-k6-round1-baseline-summary.json}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

echo "BASE_URL=$BASE_URL"
echo "VUS=$VUS"
echo "DURATION=$DURATION"
echo "KEYWORDS=$KEYWORDS"
echo "PAGE_SIZE=$PAGE_SIZE"
echo "SEARCH_LIMIT=$SEARCH_LIMIT"
echo "RECOMMENDATION_LIMIT=$RECOMMENDATION_LIMIT"
echo "GAP_LIMIT=$GAP_LIMIT"
echo "SLEEP_SECONDS=$SLEEP_SECONDS"
echo "EXPECT_PERF_FIXTURE=$EXPECT_PERF_FIXTURE"
echo "REQUIRE_AUTH_ENDPOINTS=$REQUIRE_AUTH_ENDPOINTS"
echo "FAILURE_SAMPLE_BODY_LIMIT=$FAILURE_SAMPLE_BODY_LIMIT"
echo "ACCESS_TOKEN=$([ -n "${ACCESS_TOKEN:-}" ] && echo provided || echo empty)"
echo "USER_PROJECT_ID=${USER_PROJECT_ID:-empty}"
echo "LOGIN_EMAIL=$([ -n "${LOGIN_EMAIL:-}" ] && echo provided || echo empty)"
echo "LOGIN_PASSWORD=$([ -n "${LOGIN_PASSWORD:-}" ] && echo provided || echo empty)"
echo "K6_SUMMARY_EXPORT=$K6_SUMMARY_EXPORT"

if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    VUS="$VUS" \
    DURATION="$DURATION" \
    KEYWORDS="$KEYWORDS" \
    PAGE_SIZE="$PAGE_SIZE" \
    SEARCH_LIMIT="$SEARCH_LIMIT" \
    RECOMMENDATION_LIMIT="$RECOMMENDATION_LIMIT" \
    GAP_LIMIT="$GAP_LIMIT" \
    SLEEP_SECONDS="$SLEEP_SECONDS" \
    EXPECT_PERF_FIXTURE="$EXPECT_PERF_FIXTURE" \
    REQUIRE_AUTH_ENDPOINTS="$REQUIRE_AUTH_ENDPOINTS" \
    FAILURE_SAMPLE_BODY_LIMIT="$FAILURE_SAMPLE_BODY_LIMIT" \
    ACCESS_TOKEN="${ACCESS_TOKEN:-}" \
    USER_PROJECT_ID="${USER_PROJECT_ID:-}" \
    LOGIN_EMAIL="${LOGIN_EMAIL:-}" \
    LOGIN_PASSWORD="${LOGIN_PASSWORD:-}" \
    k6 run --summary-export "$K6_SUMMARY_EXPORT" "$K6_SCRIPT"
else
    summary_dir="$(dirname "$K6_SUMMARY_EXPORT")"
    summary_file="$(basename "$K6_SUMMARY_EXPORT")"
    mkdir -p "$summary_dir"

    docker run --rm --network host \
        -e BASE_URL="$BASE_URL" \
        -e VUS="$VUS" \
        -e DURATION="$DURATION" \
        -e KEYWORDS="$KEYWORDS" \
        -e PAGE_SIZE="$PAGE_SIZE" \
        -e SEARCH_LIMIT="$SEARCH_LIMIT" \
        -e RECOMMENDATION_LIMIT="$RECOMMENDATION_LIMIT" \
        -e GAP_LIMIT="$GAP_LIMIT" \
        -e SLEEP_SECONDS="$SLEEP_SECONDS" \
        -e EXPECT_PERF_FIXTURE="$EXPECT_PERF_FIXTURE" \
        -e REQUIRE_AUTH_ENDPOINTS="$REQUIRE_AUTH_ENDPOINTS" \
        -e FAILURE_SAMPLE_BODY_LIMIT="$FAILURE_SAMPLE_BODY_LIMIT" \
        -e ACCESS_TOKEN="${ACCESS_TOKEN:-}" \
        -e USER_PROJECT_ID="${USER_PROJECT_ID:-}" \
        -e LOGIN_EMAIL="${LOGIN_EMAIL:-}" \
        -e LOGIN_PASSWORD="${LOGIN_PASSWORD:-}" \
        -v "$ROOT_DIR/performance/k6:/scripts:ro" \
        -v "$summary_dir:/k6-output" \
        grafana/k6 run --summary-export "/k6-output/$summary_file" "/scripts/$(basename "$K6_SCRIPT")"
fi

echo
echo "Round 1 k6 baseline completed."
echo "summary_export=$K6_SUMMARY_EXPORT"
