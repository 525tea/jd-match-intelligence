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
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
DEBEZIUM_SMOKE_TOPIC="${DEBEZIUM_SMOKE_TOPIC:-email.send}"
DEBEZIUM_SMOKE_RUN_ID="${DEBEZIUM_SMOKE_RUN_ID:-debezium-outbox-smoke-$(date +%Y%m%d%H%M%S)}"
DEBEZIUM_SMOKE_AGGREGATE_ID="${DEBEZIUM_SMOKE_AGGREGATE_ID:-$((8000000000 + $(date +%s) % 1000000000))}"
DEBEZIUM_SMOKE_WAIT_SECONDS="${DEBEZIUM_SMOKE_WAIT_SECONDS:-60}"
KAFKA_CONSUMER_TIMEOUT_MS="${KAFKA_CONSUMER_TIMEOUT_MS:-15000}"

cd "${ROOT_DIR}"

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

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

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "COMPOSE_FILES=${COMPOSE_FILES[*]}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "BACKEND_SERVICE=${BACKEND_SERVICE}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "DEBEZIUM_SMOKE_TOPIC=${DEBEZIUM_SMOKE_TOPIC}"
echo "DEBEZIUM_SMOKE_RUN_ID=${DEBEZIUM_SMOKE_RUN_ID}"
echo "DEBEZIUM_SMOKE_AGGREGATE_ID=${DEBEZIUM_SMOKE_AGGREGATE_ID}"
echo "DEBEZIUM_SMOKE_WAIT_SECONDS=${DEBEZIUM_SMOKE_WAIT_SECONDS}"
echo

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to run Debezium outbox smoke against real database: ${PERF_DB_NAME}"
fi

if [[ ! "${DEBEZIUM_SMOKE_RUN_ID}" =~ ^[A-Za-z0-9._:-]+$ ]]; then
  fail "DEBEZIUM_SMOKE_RUN_ID contains unsupported characters: ${DEBEZIUM_SMOKE_RUN_ID}"
fi

for service in "${MYSQL_SERVICE}" "${KAFKA_SERVICE}" "debezium-connect"; do
  if ! compose ps --services --filter status=running | grep -qx "${service}"; then
    fail "service \"${service}\" is not running"
  fi
done

echo "### Ensure outbox schema_version column exists in performance DB"
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

if ! compose ps --services --filter status=running | grep -qx "${BACKEND_SERVICE}"; then
  fail "service \"${BACKEND_SERVICE}\" is not running. Start backend after schema_version is present."
fi

backend_relay_enabled="$(compose exec -T "${BACKEND_SERVICE}" printenv JOBFLOW_OUTBOX_RELAY_ENABLED 2>/dev/null || true)"
if [[ "${backend_relay_enabled}" != "false" ]]; then
  fail "backend must run with JOBFLOW_OUTBOX_RELAY_ENABLED=false for Debezium CDC smoke. current=${backend_relay_enabled:-unset}"
fi

if ! curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
  fail "backend health is not UP"
fi

echo
echo "### Register Debezium connector"
bash performance/debezium/register-outbox-connector.sh

echo
echo "### Insert outbox event while app-level relay is disabled"
smoke_event_id="$(
  mysql_exec -Nse "
    INSERT INTO outbox_events (
      schema_version,
      aggregate_type,
      aggregate_id,
      event_type,
      payload,
      topic,
      status,
      retry_count,
      last_error,
      created_at,
      published_at
    )
    VALUES (
      1,
      'EMAIL',
      ${DEBEZIUM_SMOKE_AGGREGATE_ID},
      'EMAIL_SEND_REQUESTED',
      JSON_OBJECT(
        'to', 'user@example.com',
        'subject', CONCAT('Debezium outbox smoke ', '${DEBEZIUM_SMOKE_RUN_ID}'),
        'text', 'Debezium outbox smoke email body',
        'html', NULL,
        'smokeRunId', '${DEBEZIUM_SMOKE_RUN_ID}'
      ),
      '${DEBEZIUM_SMOKE_TOPIC}',
      'PENDING',
      0,
      NULL,
      NOW(6),
      NULL
    );
    SELECT LAST_INSERT_ID();
  "
)"

echo "debezium_smoke_event_id=${smoke_event_id}"
echo

echo "### Verify Kafka message produced by Debezium"
consumer_output_file="$(mktemp)"
consumer_error_file="$(mktemp)"

message_found=""
for ((i = 1; i <= DEBEZIUM_SMOKE_WAIT_SECONDS; i++)); do
  : > "${consumer_output_file}"
  : > "${consumer_error_file}"

  compose exec -T "${KAFKA_SERVICE}" \
    kafka-console-consumer \
      --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
      --topic "${DEBEZIUM_SMOKE_TOPIC}" \
      --from-beginning \
      --timeout-ms "${KAFKA_CONSUMER_TIMEOUT_MS}" \
      --property print.key=true \
      --property print.headers=true \
      --property key.separator='|' \
    > "${consumer_output_file}" 2> "${consumer_error_file}" || true

  message_found="$(
    grep -F "id:${smoke_event_id}|" "${consumer_output_file}" | grep -F "${DEBEZIUM_SMOKE_RUN_ID}" | tail -1 || true
  )"

  echo "debezium_kafka_wait_elapsed=${i}s message_found=$([[ -n "${message_found}" ]] && echo true || echo false)"
  [[ -n "${message_found}" ]] && break
  sleep 1
done

if [[ -z "${message_found}" ]]; then
  echo "Kafka consumer stderr:" >&2
  cat "${consumer_error_file}" >&2
  fail "Debezium Kafka message for outbox event ${smoke_event_id} was not found in topic ${DEBEZIUM_SMOKE_TOPIC}"
fi

echo "kafka_message_found=true"
echo "kafka_header_event_id_found=true"
echo "kafka_message=${message_found}"
echo

echo "### Verify backend consumer processed the Debezium message"
processed_count=""
for ((i = 1; i <= DEBEZIUM_SMOKE_WAIT_SECONDS; i++)); do
  processed_count="$(
    mysql_exec -Nse "
      SELECT COUNT(*)
      FROM processed_kafka_events
      WHERE consumer_name = 'email-send'
        AND event_id = ${smoke_event_id};
    "
  )"
  echo "consumer_process_wait_elapsed=${i}s processed_count=${processed_count}"
  [[ "${processed_count}" == "1" ]] && break
  sleep 1
done

[[ "${processed_count}" == "1" ]] || fail "backend email-send consumer did not process Debezium event ${smoke_event_id}"

outbox_row="$(
  mysql_exec -Nse "
    SELECT CONCAT(status, '|', IF(published_at IS NULL, 'NULL', 'NOT_NULL'))
    FROM outbox_events
    WHERE id = ${smoke_event_id};
  "
)"
outbox_status="${outbox_row%%|*}"
published_at_state="${outbox_row#*|}"

[[ "${outbox_status}" == "PENDING" ]] || fail "outbox status should remain PENDING when app relay is disabled. current=${outbox_status}"
[[ "${published_at_state}" == "NULL" ]] || fail "published_at should remain NULL when app relay is disabled"

rm -f "${consumer_output_file}" "${consumer_error_file}"

echo
echo "### Debezium Outbox CDC Smoke Summary"
echo "debezium_smoke_event_id=${smoke_event_id}"
echo "kafka_topic=${DEBEZIUM_SMOKE_TOPIC}"
echo "kafka_message_found=true"
echo "kafka_header_event_id_found=true"
echo "processed_count=${processed_count}"
echo "outbox_event_status=${outbox_status}"
echo "outbox_published_at=${published_at_state}"
echo
echo "Debezium outbox CDC smoke completed."
