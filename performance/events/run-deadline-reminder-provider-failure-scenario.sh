#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/performance/dataset/performance.env"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
VUS="${VUS:-20}"
DURATION="${DURATION:-5m}"
SLEEP_SECONDS="${SLEEP_SECONDS:-1}"
EXPECT_PERF_FIXTURE="${EXPECT_PERF_FIXTURE:-false}"
REQUIRE_AUTH_ENDPOINTS="${REQUIRE_AUTH_ENDPOINTS:-true}"
K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT:-/tmp/jobflow-k6-deadline-reminder-provider-failure.json}"
PREPARE_FIXTURE="${PREPARE_FIXTURE:-true}"
OBSERVE_SECONDS="${OBSERVE_SECONDS:-30}"

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
FIXTURE_SQL="${ROOT_DIR}/performance/sql/deadline-reminder-mock-load-fixture.sql"

COMPOSE_ARGS=(-f docker-compose.yml -f docker-compose.performance.yml)

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  echo "Refusing to run provider failure scenario against real database: ${PERF_DB_NAME}" >&2
  exit 1
fi

export PERF_DEADLINE_REMINDER_SCHEDULER_ENABLED=true
export PERF_DEADLINE_REMINDER_INITIAL_DELAY="${PERF_DEADLINE_REMINDER_INITIAL_DELAY:-1000}"
export PERF_DEADLINE_REMINDER_FIXED_DELAY="${PERF_DEADLINE_REMINDER_FIXED_DELAY:-5000}"
export PERF_NOTIFICATION_EMAIL_PROVIDER="${PERF_NOTIFICATION_EMAIL_PROVIDER:-mock}"
export PERF_NOTIFICATION_MOCK_EMAIL_FAIL=true
export PERF_NOTIFICATION_MOCK_EMAIL_FAILURE_REASON="${PERF_NOTIFICATION_MOCK_EMAIL_FAILURE_REASON:-performance provider failure scenario}"

cd "${ROOT_DIR}"

echo "ROOT_DIR=${ROOT_DIR}"
echo "SCENARIO=provider-failure"
echo "BASE_URL=${BASE_URL}"
echo "VUS=${VUS}"
echo "DURATION=${DURATION}"
echo "OBSERVE_SECONDS=${OBSERVE_SECONDS}"
echo "EXPECT_PERF_FIXTURE=${EXPECT_PERF_FIXTURE}"
echo "K6_SUMMARY_EXPORT=${K6_SUMMARY_EXPORT}"
echo "PREPARE_FIXTURE=${PREPARE_FIXTURE}"
echo "PERF_DEADLINE_REMINDER_SCHEDULER_ENABLED=${PERF_DEADLINE_REMINDER_SCHEDULER_ENABLED}"
echo "PERF_NOTIFICATION_EMAIL_PROVIDER=${PERF_NOTIFICATION_EMAIL_PROVIDER}"
echo "PERF_NOTIFICATION_MOCK_EMAIL_FAIL=${PERF_NOTIFICATION_MOCK_EMAIL_FAIL}"

if ! docker compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  echo "service \"${MYSQL_SERVICE}\" is not running" >&2
  echo "Start the staging performance stack first:" >&2
  echo "  REQUIRED_PORTS=\"\" bash performance/deploy/staging-performance-up.sh" >&2
  exit 1
fi

if [[ "${PREPARE_FIXTURE}" == "true" ]]; then
  echo
  echo "### Prepare deadline reminder fixture"
  docker compose exec -T "${MYSQL_SERVICE}" mysql \
    -u"${PERF_DB_USER}" \
    -p"${PERF_DB_PASSWORD}" \
    --default-character-set=utf8mb4 \
    "${PERF_DB_NAME}" < "${FIXTURE_SQL}"
fi

echo
echo "### Recreate backend/gateway with failing mock email provider"
docker compose "${COMPOSE_ARGS[@]}" up -d --no-deps --force-recreate backend gateway

echo
echo "### Wait for backend/gateway health"
for i in {1..60}; do
  backend_health="$(curl -fsS http://localhost:8080/actuator/health 2>/dev/null | jq -r '.status // "DOWN"' || true)"
  gateway_health="$(curl -fsS http://localhost:8081/actuator/health 2>/dev/null | jq -r '.status // "DOWN"' || true)"
  if [[ "${backend_health}" == "UP" && "${gateway_health}" == "UP" ]]; then
    echo "backend_health=UP"
    echo "gateway_health=UP"
    break
  fi
  if [[ "${i}" == "60" ]]; then
    echo "backend_health=${backend_health}"
    echo "gateway_health=${gateway_health}"
    echo "Timed out waiting for backend/gateway health." >&2
    exit 1
  fi
  sleep 2
done

echo
echo "### Event baseline before provider failure"
bash performance/events/event-processing-baseline-check.sh

echo
echo "### Observe failed notification attempts"
sleep "${OBSERVE_SECONDS}"

echo
echo "### Event baseline after provider failure"
bash performance/events/event-processing-baseline-check.sh

echo
echo "### Run k6 while failed reminders remain retry-scheduled"
BASE_URL="${BASE_URL}" \
LOGIN_EMAIL="${LOGIN_EMAIL}" \
LOGIN_PASSWORD="${LOGIN_PASSWORD}" \
VUS="${VUS}" \
DURATION="${DURATION}" \
SLEEP_SECONDS="${SLEEP_SECONDS}" \
EXPECT_PERF_FIXTURE="${EXPECT_PERF_FIXTURE}" \
REQUIRE_AUTH_ENDPOINTS="${REQUIRE_AUTH_ENDPOINTS}" \
K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT}" \
bash performance/k6/run-round1-baseline.sh

echo
echo "### Event baseline after k6"
bash performance/events/event-processing-baseline-check.sh

echo
echo "Deadline reminder provider failure scenario completed."
echo "scenario=provider-failure"
echo "summary_export=${K6_SUMMARY_EXPORT}"
