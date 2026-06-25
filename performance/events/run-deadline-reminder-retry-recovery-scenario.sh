#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/performance/dataset/performance.env"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
RETRY_READY_SQL="${ROOT_DIR}/performance/events/deadline-reminder-retry-ready.sql"
OBSERVE_SECONDS="${OBSERVE_SECONDS:-30}"
PREPARE_FAILURE_BACKLOG="${PREPARE_FAILURE_BACKLOG:-true}"

COMPOSE_ARGS=(-f docker-compose.yml -f docker-compose.performance.yml)

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  echo "Refusing to run retry recovery scenario against real database: ${PERF_DB_NAME}" >&2
  exit 1
fi

cd "${ROOT_DIR}"

echo "ROOT_DIR=${ROOT_DIR}"
echo "SCENARIO=retry-recovery"
echo "OBSERVE_SECONDS=${OBSERVE_SECONDS}"
echo "PREPARE_FAILURE_BACKLOG=${PREPARE_FAILURE_BACKLOG}"

if ! docker compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  echo "service \"${MYSQL_SERVICE}\" is not running" >&2
  echo "Start the staging performance stack first:" >&2
  echo "  REQUIRED_PORTS=\"\" bash performance/deploy/staging-performance-up.sh" >&2
  exit 1
fi

if [[ "${PREPARE_FAILURE_BACKLOG}" == "true" ]]; then
  echo
  echo "### Prepare failed notification backlog"
  PREPARE_FIXTURE=true \
  OBSERVE_SECONDS="${OBSERVE_SECONDS}" \
  DURATION=30s \
  VUS=5 \
  K6_SUMMARY_EXPORT=/tmp/jobflow-k6-deadline-reminder-provider-failure-for-recovery.json \
  bash performance/events/run-deadline-reminder-provider-failure-scenario.sh
fi

echo
echo "### Make pending reminder retries immediately due"
docker compose exec -T "${MYSQL_SERVICE}" mysql \
  -u"${PERF_DB_USER}" \
  -p"${PERF_DB_PASSWORD}" \
  --default-character-set=utf8mb4 \
  "${PERF_DB_NAME}" < "${RETRY_READY_SQL}"

export PERF_DEADLINE_REMINDER_SCHEDULER_ENABLED=true
export PERF_DEADLINE_REMINDER_INITIAL_DELAY="${PERF_DEADLINE_REMINDER_INITIAL_DELAY:-1000}"
export PERF_DEADLINE_REMINDER_FIXED_DELAY="${PERF_DEADLINE_REMINDER_FIXED_DELAY:-5000}"
export PERF_NOTIFICATION_EMAIL_PROVIDER="${PERF_NOTIFICATION_EMAIL_PROVIDER:-mock}"
export PERF_NOTIFICATION_MOCK_EMAIL_FAIL=false

echo
echo "### Restart backend/gateway with healthy mock email provider"
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
echo "### Event baseline before retry recovery"
bash performance/events/event-processing-baseline-check.sh

echo
echo "### Observe retry recovery"
sleep "${OBSERVE_SECONDS}"

echo
echo "### Event baseline after retry recovery"
bash performance/events/event-processing-baseline-check.sh

echo
echo "Deadline reminder retry recovery scenario completed."
