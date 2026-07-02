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
DEBEZIUM_CONNECT_SERVICE="${DEBEZIUM_CONNECT_SERVICE:-debezium-connect}"
DEBEZIUM_CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:18083}"
DEBEZIUM_CONNECTOR_NAME="${DEBEZIUM_CONNECTOR_NAME:-jobflow-outbox-mysql-connector}"

PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"

DEBEZIUM_RECOVERY_MODE="${DEBEZIUM_RECOVERY_MODE:-all}"
DEBEZIUM_RECOVERY_RUN_ID="${DEBEZIUM_RECOVERY_RUN_ID:-debezium-recovery-${DEBEZIUM_RECOVERY_MODE}-$(date +%Y%m%d%H%M%S)}"
DEBEZIUM_RECOVERY_EVENT_COUNT="${DEBEZIUM_RECOVERY_EVENT_COUNT:-10000}"
KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE:-1000}"
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID:-jobflow-backend-performance}"
DEBEZIUM_RECOVERY_WAIT_SECONDS="${DEBEZIUM_RECOVERY_WAIT_SECONDS:-240}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/debezium/$(date +%y%m%d)_debezium_recovery}"
LAST_FINAL_LAG=""

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

require_service() {
  local service="$1"
  if ! compose ps --services --filter status=running | grep -qx "${service}"; then
    fail "service \"${service}\" is not running"
  fi
}

assert_safe_database() {
  [[ "${PERF_DB_NAME}" != "jobflow" ]] || fail "Refusing to run Debezium recovery scenario against real database: ${PERF_DB_NAME}"
  [[ "${DEBEZIUM_RECOVERY_RUN_ID}" =~ ^[A-Za-z0-9._:-]+$ ]] || fail "DEBEZIUM_RECOVERY_RUN_ID contains unsupported characters: ${DEBEZIUM_RECOVERY_RUN_ID}"
  (( DEBEZIUM_RECOVERY_EVENT_COUNT > 0 )) || fail "DEBEZIUM_RECOVERY_EVENT_COUNT must be positive"
}

ensure_outbox_schema_version_column() {
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

wait_backend_up() {
  local label="$1"
  for i in {1..120}; do
    backend_health="$(curl -fsS http://localhost:8080/actuator/health/liveness 2>/dev/null | jq -r '.status // "DOWN"' || true)"
    if [[ "${backend_health}" == "UP" ]]; then
      echo "${label}_backend_health=UP"
      return
    fi
    sleep 2
  done
  fail "${label} backend health did not become UP"
}

wait_connector_state() {
  local expected_state="$1"
  for ((i = 1; i <= DEBEZIUM_RECOVERY_WAIT_SECONDS; i++)); do
    status_json="$(curl -fsS "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/status" 2>/dev/null || true)"
    state_count="$(echo "${status_json}" | grep -Eo "\"state\"[[:space:]]*:[[:space:]]*\"${expected_state}\"" | wc -l | tr -d ' ')"
    echo "connector_state_wait_elapsed=${i}s expected=${expected_state} matching_state_count=${state_count}"
    if [[ "${state_count}" -ge 2 ]]; then
      return
    fi
    sleep 1
  done
  curl -fsS "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/status" >&2 || true
  fail "Debezium connector did not reach ${expected_state}"
}

prepare_debezium_stack() {
  require_service "${MYSQL_SERVICE}"
  require_service "${KAFKA_SERVICE}"
  ensure_outbox_schema_version_column
  ensure_processed_event_id_index

  export PERF_OUTBOX_RELAY_ENABLED=false
  export PERF_OUTBOX_RELAY_PUBLISHER=kafka
  export PERF_KAFKA_CONSUMER_ENABLED=true
  export PERF_KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID}"
  export PERF_NOTIFICATION_EMAIL_PROVIDER="${PERF_NOTIFICATION_EMAIL_PROVIDER:-mock}"
  export PERF_NOTIFICATION_MOCK_EMAIL_FAIL="${PERF_NOTIFICATION_MOCK_EMAIL_FAIL:-false}"

  compose up -d "${DEBEZIUM_CONNECT_SERVICE}"
  compose up -d --no-deps --force-recreate "${BACKEND_SERVICE}"
  wait_backend_up "debezium_recovery"
  bash performance/debezium/register-outbox-connector.sh
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

seed_events() {
  local run_id="$1"
  echo
  echo "### Seed Debezium recovery events run_id=${run_id}"
  KAFKA_EVENT_LOAD_RUN_ID="${run_id}" \
  KAFKA_EVENT_LOAD_COUNT="${DEBEZIUM_RECOVERY_EVENT_COUNT}" \
  KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE}" \
  ARTIFACT_DIR="${ARTIFACT_DIR}" \
  bash performance/events/seed-kafka-email-event-load.sh \
    | tee "${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${run_id}_seed_event_load.txt"
}

processed_count_for_run() {
  local run_id="$1"
  mysql_exec -Nse "
    SELECT COUNT(*)
    FROM processed_kafka_events p
    JOIN outbox_events o ON o.id = p.event_id
    WHERE p.consumer_name = 'email-send'
      AND JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.smokeRunId')) = '${run_id}';
  "
}

outbox_status_summary_for_run() {
  local run_id="$1"
  mysql_exec -Nse "
    SELECT CONCAT(
      COALESCE(SUM(status = 'PENDING'), 0), '|',
      COALESCE(SUM(status = 'PUBLISHED'), 0), '|',
      COALESCE(SUM(status = 'FAILED'), 0), '|',
      COALESCE(COUNT(*), 0)
    )
    FROM outbox_events
    WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.smokeRunId')) = '${run_id}';
  "
}

wait_processed() {
  local run_id="$1"
  local current_processed=""
  for ((i = 1; i <= DEBEZIUM_RECOVERY_WAIT_SECONDS; i++)); do
    current_processed="$(processed_count_for_run "${run_id}")"
    echo "processed_wait_elapsed=${i}s run_id=${run_id} processed_count=${current_processed}"
    [[ "${current_processed}" == "${DEBEZIUM_RECOVERY_EVENT_COUNT}" ]] && return
    sleep 1
  done
  fail "expected processed_count=${DEBEZIUM_RECOVERY_EVENT_COUNT} for run_id=${run_id}, got ${current_processed}"
}

wait_final_lag_zero() {
  local label_prefix="$1"
  local lag=""
  for ((i = 1; i <= DEBEZIUM_RECOVERY_WAIT_SECONDS; i++)); do
    label="$(date +%Y%m%d%H%M%S)_${label_prefix}_lag_wait_${i}"
    snapshot_lag "${label}" > "${ARTIFACT_DIR}/${label}_snapshot.log"
    lag="$(read_total_lag "${label}")"
    echo "lag_wait_elapsed=${i}s total_lag=${lag}"
    if [[ "${lag}" == "0" ]]; then
      LAST_FINAL_LAG="${lag}"
      return
    fi
    sleep 1
  done
  fail "expected final lag 0 for ${label_prefix}, got ${lag}"
}

run_connector_paused_recovery() {
  local run_id="${DEBEZIUM_RECOVERY_RUN_ID}-connector-paused"
  echo
  echo "### Scenario: connector pause -> outbox insert -> connector resume"
  curl -fsS -X PUT "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/pause" >/dev/null
  wait_connector_state "PAUSED"

  seed_events "${run_id}"
  before_processed="$(processed_count_for_run "${run_id}")"
  [[ "${before_processed}" == "0" ]] || fail "expected processed_count=0 while connector is paused, got ${before_processed}"

  curl -fsS -X PUT "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/resume" >/dev/null
  wait_connector_state "RUNNING"
  wait_processed "${run_id}"
  wait_final_lag_zero "${run_id}"
  write_summary "${run_id}" "connector-paused"
}

run_backend_restart_recovery() {
  local run_id="${DEBEZIUM_RECOVERY_RUN_ID}-backend-restart"
  echo
  echo "### Scenario: backend consumer disabled -> Debezium publish -> consumer restart drain"
  export PERF_OUTBOX_RELAY_ENABLED=false
  export PERF_KAFKA_CONSUMER_ENABLED=false
  compose up -d --no-deps --force-recreate "${BACKEND_SERVICE}"
  wait_backend_up "backend_consumer_disabled"

  seed_events "${run_id}"
  before_processed="$(processed_count_for_run "${run_id}")"
  [[ "${before_processed}" == "0" ]] || fail "expected processed_count=0 while backend consumer is disabled, got ${before_processed}"

  lag_label="$(date +%Y%m%d%H%M%S)_${run_id}_backend_down_lag"
  snapshot_lag "${lag_label}" | tee "${ARTIFACT_DIR}/${lag_label}_snapshot.log"
  accumulated_lag="$(read_total_lag "${lag_label}")"
  (( accumulated_lag > 0 )) || fail "expected Kafka lag to accumulate while backend consumer is disabled, got ${accumulated_lag}"

  export PERF_KAFKA_CONSUMER_ENABLED=true
  compose up -d --no-deps --force-recreate "${BACKEND_SERVICE}"
  wait_backend_up "backend_consumer_enabled"
  wait_processed "${run_id}"
  wait_final_lag_zero "${run_id}"
  write_summary "${run_id}" "backend-restart"
}

write_summary() {
  local run_id="$1"
  local mode="$2"
  local summary_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${mode}_summary.txt"
  status_row="$(outbox_status_summary_for_run "${run_id}")"
  pending_count="${status_row%%|*}"
  rest="${status_row#*|}"
  published_count="${rest%%|*}"
  rest="${rest#*|}"
  failed_count="${rest%%|*}"
  total_count="${rest#*|}"
  current_processed="$(processed_count_for_run "${run_id}")"

  {
    echo "mode=${mode}"
    echo "run_id=${run_id}"
    echo "event_count=${DEBEZIUM_RECOVERY_EVENT_COUNT}"
    echo "outbox_pending=${pending_count}"
    echo "outbox_published=${published_count}"
    echo "outbox_failed=${failed_count}"
    echo "outbox_total=${total_count}"
    echo "processed_count=${current_processed}"
    echo "final_lag=${LAST_FINAL_LAG}"
    echo "artifact_dir=${ARTIFACT_DIR}"
  } | tee "${summary_file}"
}

case "${DEBEZIUM_RECOVERY_MODE}" in
  all|connector-paused|backend-restart)
    ;;
  *)
    fail "Unsupported DEBEZIUM_RECOVERY_MODE=${DEBEZIUM_RECOVERY_MODE}. Use all, connector-paused, or backend-restart."
    ;;
esac

assert_safe_database

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "DEBEZIUM_RECOVERY_MODE=${DEBEZIUM_RECOVERY_MODE}"
echo "DEBEZIUM_RECOVERY_RUN_ID=${DEBEZIUM_RECOVERY_RUN_ID}"
echo "DEBEZIUM_RECOVERY_EVENT_COUNT=${DEBEZIUM_RECOVERY_EVENT_COUNT}"
echo "KAFKA_CONSUMER_GROUP_ID=${KAFKA_CONSUMER_GROUP_ID}"
echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
echo

prepare_debezium_stack

case "${DEBEZIUM_RECOVERY_MODE}" in
  connector-paused)
    run_connector_paused_recovery
    ;;
  backend-restart)
    run_backend_restart_recovery
    ;;
  all)
    run_connector_paused_recovery
    run_backend_restart_recovery
    ;;
esac

echo
echo "Debezium recovery scenario completed."
echo "mode=${DEBEZIUM_RECOVERY_MODE}"
echo "run_id=${DEBEZIUM_RECOVERY_RUN_ID}"
echo "final_lag=${LAST_FINAL_LAG}"
echo "artifact_dir=${ARTIFACT_DIR}"
