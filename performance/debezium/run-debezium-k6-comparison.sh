#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"

if [[ -f "${ENV_FILE}" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// }" ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    value="${value%\"}" value="${value#\"}"
    value="${value%\'}" value="${value#\'}"
    export "$key=$value"
  done < "${ENV_FILE}"
fi

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.performance.yml)
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
GATEWAY_SERVICE="${GATEWAY_SERVICE:-gateway}"
DEBEZIUM_CONNECT_SERVICE="${DEBEZIUM_CONNECT_SERVICE:-debezium-connect}"
DEBEZIUM_CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:18083}"
DEBEZIUM_CONNECTOR_NAME="${DEBEZIUM_CONNECTOR_NAME:-jobflow-outbox-mysql-connector}"

PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"

PHASE="${PHASE:-full}"
MODE="${MODE:-app-relay-baseline}"
BASE_URL="${BASE_URL:-http://localhost:8081/api}"
LOGIN_EMAIL="${LOGIN_EMAIL:-frontend-demo@example.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-password123}"
VUS="${VUS:-500}"
DURATION="${DURATION:-5m}"
SLEEP_SECONDS="${SLEEP_SECONDS:-1}"
ENDPOINTS="${ENDPOINTS:-jobs_search,recommendations_jobs,gap_analysis}"
ENDPOINT_ORDER_MODE="${ENDPOINT_ORDER_MODE:-rotated}"
K6_SCENARIO_MODE="${K6_SCENARIO_MODE:-ramping-vus}"
RAMP_UP_DURATION="${RAMP_UP_DURATION:-2m}"
STEADY_DURATION="${STEADY_DURATION:-3m}"
RAMP_DOWN_DURATION="${RAMP_DOWN_DURATION:-30s}"
EXPECT_PERF_FIXTURE="${EXPECT_PERF_FIXTURE:-false}"
REQUIRE_AUTH_ENDPOINTS="${REQUIRE_AUTH_ENDPOINTS:-true}"

DEBEZIUM_K6_RUN_ID="${DEBEZIUM_K6_RUN_ID:-debezium-k6-${MODE}-$(date +%Y%m%d%H%M%S)}"
KAFKA_EVENT_LOAD_COUNT="${KAFKA_EVENT_LOAD_COUNT:-10000}"
KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE:-1000}"
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID:-jobflow-backend-performance}"
KAFKA_FINAL_LAG_WAIT_SECONDS="${KAFKA_FINAL_LAG_WAIT_SECONDS:-180}"
K6_EVENT_SEED_DELAY_SECONDS="${K6_EVENT_SEED_DELAY_SECONDS:-20}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/debezium/$(date +%y%m%d)_debezium_k6_comparison}"
K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT:-${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${MODE}_k6.json}"
FINAL_TOTAL_LAG=""

cd "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

mysql_exec() {
  compose exec -T -e MYSQL_PWD="${PERF_DB_PASSWORD}" "${MYSQL_SERVICE}" mysql \
    -u"${PERF_DB_USER}" \
    --default-character-set=utf8mb4 \
    "$@" \
    "${PERF_DB_NAME}"
}

mysql_root_exec() {
  compose exec -T -e MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" "${MYSQL_SERVICE}" mysql \
    -u"${MYSQL_ROOT_USER}" \
    --default-character-set=utf8mb4 \
    "$@" \
    "${PERF_DB_NAME}"
}

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

assert_safe_database() {
  [[ "${PERF_DB_NAME}" != "jobflow" ]] || fail "Refusing to run Debezium k6 comparison against real database: ${PERF_DB_NAME}"
  [[ "${DEBEZIUM_K6_RUN_ID}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "DEBEZIUM_K6_RUN_ID contains unsupported characters: ${DEBEZIUM_K6_RUN_ID}"
  (( KAFKA_EVENT_LOAD_COUNT > 0 )) || fail "KAFKA_EVENT_LOAD_COUNT must be positive"
}

require_service() {
  local service="$1"
  if ! compose ps --services --filter status=running | grep -qx "${service}"; then
    fail "service \"${service}\" is not running"
  fi
}

wait_backend_gateway_up() {
  local label="$1"
  for i in {1..120}; do
    backend_health="$(curl -fsS http://localhost:8080/actuator/health/liveness 2>/dev/null | jq -r '.status // "DOWN"' || true)"
    gateway_health="$(curl -fsS http://localhost:8081/actuator/health 2>/dev/null | jq -r '.status // "DOWN"' || true)"
    if [[ "${backend_health}" == "UP" && "${gateway_health}" == "UP" ]]; then
      echo "${label}_backend_health=UP"
      echo "${label}_gateway_health=UP"
      return
    fi
    sleep 2
  done
  fail "${label} backend/gateway health did not become UP"
}

ensure_outbox_schema_version_column() {
  echo "### Ensure outbox schema_version column exists"
  schema_version_column_count="$(
    mysql_root_exec -Nse "
      SELECT COUNT(*)
      FROM information_schema.columns
      WHERE table_schema = DATABASE()
        AND table_name = 'outbox_events'
        AND column_name = 'schema_version';
    "
  )"

  if [[ "${schema_version_column_count}" == "0" ]]; then
    mysql_root_exec -e "
      ALTER TABLE outbox_events
        ADD COLUMN schema_version INT NOT NULL DEFAULT 1 AFTER id;
    "
    echo "outbox_schema_version_column_added=true"
  else
    echo "outbox_schema_version_column_added=false"
  fi
}

ensure_processed_event_id_index() {
  echo "### Ensure processed_kafka_events event_id index exists"
  processed_event_id_index_count="$(
    mysql_root_exec -Nse "
      SELECT COUNT(*)
      FROM information_schema.statistics
      WHERE table_schema = DATABASE()
        AND table_name = 'processed_kafka_events'
        AND index_name = 'idx_processed_kafka_events_event_id';
    "
  )"

  if [[ "${processed_event_id_index_count}" == "0" ]]; then
    mysql_root_exec -e "
      ALTER TABLE processed_kafka_events
        ADD KEY idx_processed_kafka_events_event_id (event_id);
    "
    echo "processed_event_id_index_added=true"
  else
    echo "processed_event_id_index_added=false"
  fi
}

delete_debezium_connector_if_exists() {
  if curl -fsS "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}" >/dev/null 2>&1; then
    curl -fsS -X DELETE "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}" >/dev/null
    echo "debezium_connector_deleted=true"
  else
    echo "debezium_connector_deleted=false"
  fi
}

configure_mode() {
  case "${MODE}" in
    app-relay-baseline)
      export PERF_OUTBOX_RELAY_ENABLED=true
      export PERF_OUTBOX_RELAY_PUBLISHER=kafka
      export PERF_KAFKA_CONSUMER_ENABLED=true
      ;;
    debezium-cdc-after)
      export PERF_OUTBOX_RELAY_ENABLED=false
      export PERF_OUTBOX_RELAY_PUBLISHER=kafka
      export PERF_KAFKA_CONSUMER_ENABLED=true
      ;;
    *)
      fail "Unsupported MODE=${MODE}. Use app-relay-baseline or debezium-cdc-after."
      ;;
  esac

  export PERF_NOTIFICATION_EMAIL_PROVIDER="${PERF_NOTIFICATION_EMAIL_PROVIDER:-mock}"
  export PERF_NOTIFICATION_MOCK_EMAIL_FAIL="${PERF_NOTIFICATION_MOCK_EMAIL_FAIL:-false}"
  export PERF_OUTBOX_RELAY_BATCH_SIZE="${PERF_OUTBOX_RELAY_BATCH_SIZE:-500}"
  export PERF_OUTBOX_RELAY_FIXED_DELAY="${PERF_OUTBOX_RELAY_FIXED_DELAY:-1000}"
  export PERF_OUTBOX_RELAY_INITIAL_DELAY="${PERF_OUTBOX_RELAY_INITIAL_DELAY:-1000}"
  export PERF_KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID}"
  export PERF_KAFKA_CONSUMER_CONCURRENCY="${PERF_KAFKA_CONSUMER_CONCURRENCY:-3}"
}

prepare_stack() {
  require_service "${MYSQL_SERVICE}"
  require_service "${KAFKA_SERVICE}"
  ensure_outbox_schema_version_column
  ensure_processed_event_id_index

  if [[ "${MODE}" == "debezium-cdc-after" ]]; then
    compose up -d "${DEBEZIUM_CONNECT_SERVICE}"
  fi

  echo
  echo "### Recreate backend/gateway for ${MODE}"
  compose up -d --no-deps --force-recreate "${BACKEND_SERVICE}" "${GATEWAY_SERVICE}"
  wait_backend_gateway_up "${MODE}"

  if [[ "${MODE}" == "app-relay-baseline" ]]; then
    delete_debezium_connector_if_exists
  else
    bash performance/debezium/register-outbox-connector.sh
  fi
}

snapshot_lag() {
  local label="$1"
  SNAPSHOT_LABEL="${label}" \
  ARTIFACT_DIR="${ARTIFACT_DIR}" \
  KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID}" \
  bash performance/events/kafka-consumer-lag-snapshot.sh
}

read_total_lag() {
  local label="$1"
  local lag_file="${ARTIFACT_DIR}/${label}_consumer_group_lag.txt"
  awk '
    NR == 1 { next }
    $0 ~ /^[[:space:]]*$/ { next }
    $6 ~ /^[0-9]+$/ { sum += $6 }
    END { print sum + 0 }
  ' "${lag_file}"
}

event_status_summary() {
  mysql_exec -Nse "
    SELECT CONCAT(
      COALESCE(SUM(status = 'PENDING'), 0), '|',
      COALESCE(SUM(status = 'PUBLISHED'), 0), '|',
      COALESCE(SUM(status = 'FAILED'), 0), '|',
      COALESCE(COUNT(*), 0)
    )
    FROM outbox_events
    WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.smokeRunId')) = '${DEBEZIUM_K6_RUN_ID}';
  "
}

processed_count() {
  mysql_exec -Nse "
    SELECT COUNT(*)
    FROM processed_kafka_events p
    JOIN outbox_events o ON o.id = p.event_id
    WHERE p.consumer_name = 'email-send'
      AND JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.smokeRunId')) = '${DEBEZIUM_K6_RUN_ID}';
  "
}

seed_event_load() {
  echo
  echo "### Seed outbox event load for ${MODE}"
  KAFKA_EVENT_LOAD_RUN_ID="${DEBEZIUM_K6_RUN_ID}" \
  KAFKA_EVENT_LOAD_COUNT="${KAFKA_EVENT_LOAD_COUNT}" \
  KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE}" \
  ARTIFACT_DIR="${ARTIFACT_DIR}" \
  bash performance/events/seed-kafka-email-event-load.sh \
    | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${MODE}_seed_event_load.txt"
}

run_k6() {
  echo
  echo "### Run k6 for ${MODE}"
  BASE_URL="${BASE_URL}" \
  LOGIN_EMAIL="${LOGIN_EMAIL}" \
  LOGIN_PASSWORD="${LOGIN_PASSWORD}" \
  VUS="${VUS}" \
  DURATION="${DURATION}" \
  SLEEP_SECONDS="${SLEEP_SECONDS}" \
  ENDPOINTS="${ENDPOINTS}" \
  ENDPOINT_ORDER_MODE="${ENDPOINT_ORDER_MODE}" \
  K6_SCENARIO_MODE="${K6_SCENARIO_MODE}" \
  RAMP_UP_DURATION="${RAMP_UP_DURATION}" \
  STEADY_DURATION="${STEADY_DURATION}" \
  RAMP_DOWN_DURATION="${RAMP_DOWN_DURATION}" \
  EXPECT_PERF_FIXTURE="${EXPECT_PERF_FIXTURE}" \
  REQUIRE_AUTH_ENDPOINTS="${REQUIRE_AUTH_ENDPOINTS}" \
  K6_SUMMARY_EXPORT="${K6_SUMMARY_EXPORT}" \
  bash performance/k6/run-round1-baseline.sh
}

run_k6_with_concurrent_seed() {
  run_k6 &
  local k6_pid=$!
  sleep "${K6_EVENT_SEED_DELAY_SECONDS}"
  if ! seed_event_load; then
    echo "seed_event_load failed; stopping k6 pid=${k6_pid}" >&2
    kill "${k6_pid}" 2>/dev/null || true
    wait "${k6_pid}" 2>/dev/null || true
    return 1
  fi
  wait "${k6_pid}"
}

wait_final_lag_and_processed() {
  local final_lag=""
  local current_processed=""
  for ((i = 1; i <= KAFKA_FINAL_LAG_WAIT_SECONDS; i++)); do
    local label
    label="$(date +%Y%m%d%H%M%S)_${MODE}_final_wait_${i}"
    snapshot_lag "${label}" > "${ARTIFACT_DIR}/${label}_snapshot.log"
    final_lag="$(read_total_lag "${label}")"
    current_processed="$(processed_count)"
    echo "final_wait_elapsed=${i}s total_lag=${final_lag} processed_count=${current_processed}"
    if [[ "${final_lag}" == "0" && "${current_processed}" == "${KAFKA_EVENT_LOAD_COUNT}" ]]; then
      FINAL_TOTAL_LAG="${final_lag}"
      break
    fi
    sleep 1
  done

  [[ "${final_lag}" == "0" ]] || fail "expected final lag 0, got ${final_lag}"
  [[ "${current_processed}" == "${KAFKA_EVENT_LOAD_COUNT}" ]] || fail "expected processed_count=${KAFKA_EVENT_LOAD_COUNT}, got ${current_processed}"
  FINAL_TOTAL_LAG="${final_lag}"
}

write_summary() {
  local summary_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${MODE}_summary.txt"
  status_row="$(event_status_summary)"
  pending_count="${status_row%%|*}"
  rest="${status_row#*|}"
  published_count="${rest%%|*}"
  rest="${rest#*|}"
  failed_count="${rest%%|*}"
  total_count="${rest#*|}"
  current_processed="$(processed_count)"

  {
    echo "mode=${MODE}"
    echo "run_id=${DEBEZIUM_K6_RUN_ID}"
    echo "vus=${VUS}"
    echo "duration=${DURATION}"
    echo "event_count=${KAFKA_EVENT_LOAD_COUNT}"
    echo "k6_summary_export=${K6_SUMMARY_EXPORT}"
    echo "outbox_pending=${pending_count}"
    echo "outbox_published=${published_count}"
    echo "outbox_failed=${failed_count}"
    echo "outbox_total=${total_count}"
    echo "processed_count=${current_processed}"
    echo "final_lag=${FINAL_TOTAL_LAG}"
    echo "artifact_dir=${ARTIFACT_DIR}"
  } | tee "${summary_file}"
}

case "${PHASE}" in
  full|prepare|seed|k6-only|finish)
    ;;
  *)
    fail "Unsupported PHASE=${PHASE}. Use full, prepare, seed, k6-only, or finish."
    ;;
esac

assert_safe_database
configure_mode

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "PHASE=${PHASE}"
echo "MODE=${MODE}"
echo "BASE_URL=${BASE_URL}"
echo "VUS=${VUS}"
echo "DURATION=${DURATION}"
echo "ENDPOINTS=${ENDPOINTS}"
echo "ENDPOINT_ORDER_MODE=${ENDPOINT_ORDER_MODE}"
echo "K6_SCENARIO_MODE=${K6_SCENARIO_MODE}"
echo "RAMP_UP_DURATION=${RAMP_UP_DURATION}"
echo "STEADY_DURATION=${STEADY_DURATION}"
echo "RAMP_DOWN_DURATION=${RAMP_DOWN_DURATION}"
echo "K6_EVENT_SEED_DELAY_SECONDS=${K6_EVENT_SEED_DELAY_SECONDS}"
echo "DEBEZIUM_K6_RUN_ID=${DEBEZIUM_K6_RUN_ID}"
echo "KAFKA_EVENT_LOAD_COUNT=${KAFKA_EVENT_LOAD_COUNT}"
echo "KAFKA_CONSUMER_GROUP_ID=${KAFKA_CONSUMER_GROUP_ID}"
echo "PERF_OUTBOX_RELAY_ENABLED=${PERF_OUTBOX_RELAY_ENABLED}"
echo "PERF_KAFKA_CONSUMER_ENABLED=${PERF_KAFKA_CONSUMER_ENABLED}"
echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
echo "K6_SUMMARY_EXPORT=${K6_SUMMARY_EXPORT}"
echo

if [[ "${PHASE}" == "prepare" || "${PHASE}" == "full" ]]; then
  prepare_stack
  bash performance/events/event-processing-baseline-check.sh \
    | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${MODE}_event_baseline_before.txt"
  snapshot_lag "$(date +%Y%m%d%H%M%S)_${MODE}_before"
fi

case "${PHASE}" in
  prepare)
    echo "Debezium k6 comparison prepare phase completed."
    exit 0
    ;;
  seed)
    seed_event_load
    exit 0
    ;;
  k6-only)
    run_k6
    exit 0
    ;;
  full)
    run_k6_with_concurrent_seed
    ;;
esac

if [[ "${PHASE}" == "finish" || "${PHASE}" == "full" ]]; then
  wait_final_lag_and_processed
  snapshot_lag "$(date +%Y%m%d%H%M%S)_${MODE}_after"
  bash performance/events/event-processing-baseline-check.sh \
    | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${MODE}_event_baseline_after.txt"
  write_summary
fi

echo
echo "Debezium k6 comparison scenario completed."
echo "mode=${MODE}"
echo "run_id=${DEBEZIUM_K6_RUN_ID}"
echo "summary_export=${K6_SUMMARY_EXPORT}"
if [[ "${PHASE}" == "finish" || "${PHASE}" == "full" ]]; then
  echo "processed_count=$(processed_count)"
  echo "final_lag=${FINAL_TOTAL_LAG}"
fi
echo "artifact_dir=${ARTIFACT_DIR}"
