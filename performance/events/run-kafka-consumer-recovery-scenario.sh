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
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID:-jobflow-backend-performance}"
KAFKA_RECOVERY_EVENT_COUNT="${KAFKA_RECOVERY_EVENT_COUNT:-10000}"
KAFKA_RECOVERY_RUN_ID="${KAFKA_RECOVERY_RUN_ID:-kafka-consumer-recovery-$(date +%Y%m%d%H%M%S)}"
KAFKA_RECOVERY_WAIT_SECONDS="${KAFKA_RECOVERY_WAIT_SECONDS:-120}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/kafka/$(date +%y%m%d)_kafka_consumer_failure_recovery}"

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

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

wait_backend_up() {
  local label="$1"
  for i in {1..90}; do
    backend_health="$(curl -fsS http://localhost:8080/actuator/health/liveness 2>/dev/null | jq -r '.status // "DOWN"' || true)"
    if [[ "${backend_health}" == "UP" ]]; then
      echo "${label}_backend_health=UP"
      return
    fi
    sleep 2
  done
  fail "${label} backend health did not become UP"
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

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to run Kafka recovery scenario against real database: ${PERF_DB_NAME}"
fi

if (( KAFKA_RECOVERY_EVENT_COUNT <= 0 )); then
  fail "KAFKA_RECOVERY_EVENT_COUNT must be positive"
fi

if ! compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  fail "service \"${MYSQL_SERVICE}\" is not running"
fi

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

summary_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_RECOVERY_RUN_ID}_consumer_recovery_summary.txt"

{
  echo "ROOT_DIR=${ROOT_DIR}"
  echo "ENV_FILE=${ENV_FILE}"
  echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
  echo "KAFKA_RECOVERY_RUN_ID=${KAFKA_RECOVERY_RUN_ID}"
  echo "KAFKA_RECOVERY_EVENT_COUNT=${KAFKA_RECOVERY_EVENT_COUNT}"
  echo "KAFKA_CONSUMER_GROUP_ID=${KAFKA_CONSUMER_GROUP_ID}"
  echo
} | tee "${summary_file}"

echo "### Recreate backend with Kafka consumer disabled"
PERF_KAFKA_CONSUMER_ENABLED=false \
PERF_OUTBOX_RELAY_PUBLISHER=kafka \
compose up -d --no-deps --force-recreate backend
wait_backend_up "consumer_disabled"

echo
echo "### Seed outbox events while consumer is disabled"
KAFKA_EVENT_LOAD_RUN_ID="${KAFKA_RECOVERY_RUN_ID}" \
KAFKA_EVENT_LOAD_COUNT="${KAFKA_RECOVERY_EVENT_COUNT}" \
KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE:-1000}" \
ARTIFACT_DIR="${ARTIFACT_DIR}" \
bash performance/events/seed-kafka-email-event-load.sh | tee -a "${summary_file}"

echo
echo "### Wait for outbox relay to publish events"
published_count=0
for ((i = 1; i <= KAFKA_RECOVERY_WAIT_SECONDS; i++)); do
  row="$(
    mysql_exec -Nse "
      SELECT CONCAT(
        SUM(status = 'PUBLISHED'), '|',
        SUM(status = 'PENDING'), '|',
        SUM(status = 'FAILED')
      )
      FROM outbox_events
      WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.smokeRunId')) = '${KAFKA_RECOVERY_RUN_ID}';
    "
  )"
  published_count="${row%%|*}"
  rest="${row#*|}"
  pending_count="${rest%%|*}"
  failed_count="${rest#*|}"
  echo "publish_wait_elapsed=${i}s published=${published_count} pending=${pending_count} failed=${failed_count}" | tee -a "${summary_file}"
  if [[ "${published_count}" == "${KAFKA_RECOVERY_EVENT_COUNT}" && "${pending_count}" == "0" && "${failed_count}" == "0" ]]; then
    break
  fi
  sleep 1
done

if [[ "${published_count}" != "${KAFKA_RECOVERY_EVENT_COUNT}" ]]; then
  fail "expected ${KAFKA_RECOVERY_EVENT_COUNT} published outbox events, got ${published_count}"
fi

lag_accumulated_label="$(date +%Y%m%d%H%M%S)_${KAFKA_RECOVERY_RUN_ID}_consumer_disabled_lag_accumulated"
snapshot_lag "${lag_accumulated_label}" | tee -a "${summary_file}"
lag_accumulated="$(read_total_lag "${lag_accumulated_label}")"

if (( lag_accumulated <= 0 )); then
  fail "expected Kafka lag to accumulate while consumer is disabled, got ${lag_accumulated}"
fi

echo
echo "### Recreate backend with Kafka consumer enabled"
PERF_KAFKA_CONSUMER_ENABLED=true \
PERF_OUTBOX_RELAY_PUBLISHER=kafka \
compose up -d --no-deps --force-recreate backend
wait_backend_up "consumer_enabled"

drained_lag=""
for ((i = 1; i <= KAFKA_RECOVERY_WAIT_SECONDS; i++)); do
  drain_label="$(date +%Y%m%d%H%M%S)_${KAFKA_RECOVERY_RUN_ID}_consumer_restarted_drain_${i}"
  snapshot_lag "${drain_label}" > "${ARTIFACT_DIR}/${drain_label}_snapshot.log"
  drained_lag="$(read_total_lag "${drain_label}")"
  echo "drain_wait_elapsed=${i}s total_lag=${drained_lag}" | tee -a "${summary_file}"
  if [[ "${drained_lag}" == "0" ]]; then
    cat "${ARTIFACT_DIR}/${drain_label}_snapshot.log" >> "${summary_file}"
    break
  fi
  sleep 1
done

if [[ "${drained_lag}" != "0" ]]; then
  fail "expected Kafka lag to drain to 0 after consumer restart, got ${drained_lag}"
fi

processed_count="$(
  mysql_exec -Nse "
    SELECT COUNT(*)
    FROM processed_kafka_events p
    JOIN outbox_events o ON o.id = p.event_id
    WHERE p.consumer_name = 'email-send'
      AND JSON_UNQUOTE(JSON_EXTRACT(o.payload, '$.smokeRunId')) = '${KAFKA_RECOVERY_RUN_ID}';
  "
)"

if [[ "${processed_count}" != "${KAFKA_RECOVERY_EVENT_COUNT}" ]]; then
  fail "expected ${KAFKA_RECOVERY_EVENT_COUNT} processed events after drain, got ${processed_count}"
fi

{
  echo
  echo "### Kafka Consumer Recovery Summary"
  echo "run_id=${KAFKA_RECOVERY_RUN_ID}"
  echo "seeded_outbox_events=${KAFKA_RECOVERY_EVENT_COUNT}"
  echo "lag_accumulated=${lag_accumulated}"
  echo "final_lag=${drained_lag}"
  echo "processed_count=${processed_count}"
  echo "summary_file=${summary_file}"
  echo "Kafka consumer recovery scenario completed."
} | tee -a "${summary_file}"
