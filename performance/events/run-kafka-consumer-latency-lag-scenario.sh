#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/performance/dataset/performance.env}"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

SCENARIO="${SCENARIO:-kafka-consumer-after}"
BASE_URL="${BASE_URL:-http://localhost:8081/api}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
VUS="${VUS:-500}"
DURATION="${DURATION:-5m}"
SLEEP_SECONDS="${SLEEP_SECONDS:-1}"
EXPECT_PERF_FIXTURE="${EXPECT_PERF_FIXTURE:-false}"
REQUIRE_AUTH_ENDPOINTS="${REQUIRE_AUTH_ENDPOINTS:-true}"
KAFKA_EVENT_LOAD_COUNT="${KAFKA_EVENT_LOAD_COUNT:-60000}"
KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE:-1000}"
KAFKA_EVENT_LOAD_RUN_ID="${KAFKA_EVENT_LOAD_RUN_ID:-kafka-latency-lag-${SCENARIO}-$(date +%Y%m%d%H%M%S)}"
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID:-jobflow-backend-performance}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/kafka/$(date +%y%m%d)_kafka_consumer_latency_lag}"
K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT:-${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${SCENARIO}_k6.json}"

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"

COMPOSE_ARGS=(-f docker-compose.yml -f docker-compose.performance.yml)

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  echo "Refusing to run Kafka latency/lag scenario against real database: ${PERF_DB_NAME}" >&2
  exit 1
fi

case "${SCENARIO}" in
  api-only-baseline)
    PREPARE_EVENT_LOAD=false
    export PERF_OUTBOX_RELAY_PUBLISHER=kafka
    export PERF_KAFKA_CONSUMER_ENABLED=true
    ;;
  kafka-consumer-after)
    PREPARE_EVENT_LOAD=true
    export PERF_OUTBOX_RELAY_PUBLISHER=kafka
    export PERF_KAFKA_CONSUMER_ENABLED=true
    ;;
  *)
    echo "Unsupported SCENARIO=${SCENARIO}. Use api-only-baseline or kafka-consumer-after." >&2
    exit 1
    ;;
esac

export PERF_NOTIFICATION_EMAIL_PROVIDER="${PERF_NOTIFICATION_EMAIL_PROVIDER:-mock}"
export PERF_NOTIFICATION_MOCK_EMAIL_FAIL="${PERF_NOTIFICATION_MOCK_EMAIL_FAIL:-false}"
export PERF_OUTBOX_RELAY_BATCH_SIZE="${PERF_OUTBOX_RELAY_BATCH_SIZE:-500}"
export PERF_OUTBOX_RELAY_FIXED_DELAY="${PERF_OUTBOX_RELAY_FIXED_DELAY:-1000}"
export PERF_OUTBOX_RELAY_INITIAL_DELAY="${PERF_OUTBOX_RELAY_INITIAL_DELAY:-1000}"
export PERF_KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID}"
export PERF_KAFKA_CONSUMER_CONCURRENCY="${PERF_KAFKA_CONSUMER_CONCURRENCY:-3}"

cd "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "SCENARIO=${SCENARIO}"
echo "BASE_URL=${BASE_URL}"
echo "VUS=${VUS}"
echo "DURATION=${DURATION}"
echo "SLEEP_SECONDS=${SLEEP_SECONDS}"
echo "EXPECT_PERF_FIXTURE=${EXPECT_PERF_FIXTURE}"
echo "REQUIRE_AUTH_ENDPOINTS=${REQUIRE_AUTH_ENDPOINTS}"
echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
echo "K6_SUMMARY_EXPORT=${K6_SUMMARY_EXPORT}"
echo "KAFKA_EVENT_LOAD_RUN_ID=${KAFKA_EVENT_LOAD_RUN_ID}"
echo "KAFKA_EVENT_LOAD_COUNT=${KAFKA_EVENT_LOAD_COUNT}"
echo "KAFKA_CONSUMER_GROUP_ID=${KAFKA_CONSUMER_GROUP_ID}"
echo "PERF_OUTBOX_RELAY_PUBLISHER=${PERF_OUTBOX_RELAY_PUBLISHER}"
echo "PERF_KAFKA_CONSUMER_ENABLED=${PERF_KAFKA_CONSUMER_ENABLED}"
echo "PERF_KAFKA_CONSUMER_CONCURRENCY=${PERF_KAFKA_CONSUMER_CONCURRENCY}"
echo "PERF_OUTBOX_RELAY_BATCH_SIZE=${PERF_OUTBOX_RELAY_BATCH_SIZE}"
echo "PERF_OUTBOX_RELAY_FIXED_DELAY=${PERF_OUTBOX_RELAY_FIXED_DELAY}"
echo

if ! docker compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  echo "service \"${MYSQL_SERVICE}\" is not running" >&2
  echo "Start the staging performance stack first:" >&2
  echo "  REQUIRED_PORTS=\"\" bash performance/deploy/staging-performance-up.sh" >&2
  exit 1
fi

echo "### Recreate backend/gateway with Kafka scenario settings"
docker compose "${COMPOSE_ARGS[@]}" up -d --no-deps --force-recreate backend gateway

echo
echo "### Wait for backend/gateway health"
for i in {1..90}; do
  backend_health="$(curl -fsS http://localhost:8080/actuator/health/liveness 2>/dev/null | jq -r '.status // "DOWN"' || true)"
  gateway_health="$(curl -fsS http://localhost:8081/actuator/health 2>/dev/null | jq -r '.status // "DOWN"' || true)"
  if [[ "${backend_health}" == "UP" && "${gateway_health}" == "UP" ]]; then
    echo "backend_health=UP"
    echo "gateway_health=UP"
    break
  fi
  if [[ "${i}" == "90" ]]; then
    echo "backend_health=${backend_health}"
    echo "gateway_health=${gateway_health}"
    echo "Timed out waiting for backend/gateway health." >&2
    exit 1
  fi
  sleep 2
done

echo
echo "### Event baseline before scenario"
bash performance/events/event-processing-baseline-check.sh | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${SCENARIO}_event_baseline_before.txt"

echo
echo "### Kafka lag before scenario"
SNAPSHOT_LABEL="$(date +%Y%m%d%H%M%S)_${SCENARIO}_before" \
ARTIFACT_DIR="${ARTIFACT_DIR}" \
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID}" \
bash performance/events/kafka-consumer-lag-snapshot.sh

if [[ "${PREPARE_EVENT_LOAD}" == "true" ]]; then
  echo
  echo "### Seed Kafka event load through outbox"
  KAFKA_EVENT_LOAD_RUN_ID="${KAFKA_EVENT_LOAD_RUN_ID}" \
  KAFKA_EVENT_LOAD_COUNT="${KAFKA_EVENT_LOAD_COUNT}" \
  KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE}" \
  bash performance/events/seed-kafka-email-event-load.sh
fi

echo
echo "### Run k6 while Kafka consumer path is active"
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
echo "### Kafka lag after k6"
SNAPSHOT_LABEL="$(date +%Y%m%d%H%M%S)_${SCENARIO}_after" \
ARTIFACT_DIR="${ARTIFACT_DIR}" \
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID}" \
bash performance/events/kafka-consumer-lag-snapshot.sh

echo
echo "### Event baseline after scenario"
bash performance/events/event-processing-baseline-check.sh | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${SCENARIO}_event_baseline_after.txt"

echo
echo "### Duplicate replay idempotency smoke"
bash performance/events/kafka-duplicate-replay-idempotency-smoke.sh | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${SCENARIO}_idempotency_smoke.txt"

echo
echo "Kafka consumer latency/lag scenario completed."
echo "scenario=${SCENARIO}"
echo "summary_export=${K6_SUMMARY_EXPORT}"
echo "artifact_dir=${ARTIFACT_DIR}"
