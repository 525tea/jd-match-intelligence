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
CONNECT_SERVICE="${CONNECT_SERVICE:-debezium-connect}"
MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"
DEBEZIUM_CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:18083}"
DEBEZIUM_CONNECTOR_NAME="${DEBEZIUM_CONNECTOR_NAME:-jobflow-outbox-mysql-connector}"
DEBEZIUM_DB_HOST="${DEBEZIUM_DB_HOST:-mysql}"
DEBEZIUM_DB_PORT="${DEBEZIUM_DB_PORT:-3306}"
DEBEZIUM_DB_USER="${DEBEZIUM_DB_USER:-root}"
DEBEZIUM_DB_PASSWORD="${DEBEZIUM_DB_PASSWORD:-${MYSQL_ROOT_PASSWORD:-root}}"
DEBEZIUM_CONNECTOR_DATABASE_SERVER_ID="${DEBEZIUM_CONNECTOR_DATABASE_SERVER_ID:-223345}"
DEBEZIUM_TOPIC_PREFIX="${DEBEZIUM_TOPIC_PREFIX:-jobflow-cdc}"
DEBEZIUM_SCHEMA_HISTORY_TOPIC="${DEBEZIUM_SCHEMA_HISTORY_TOPIC:-schema-changes.jobflow}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
DEBEZIUM_WAIT_SECONDS="${DEBEZIUM_WAIT_SECONDS:-60}"

cd "${ROOT_DIR}"

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "COMPOSE_FILES=${COMPOSE_FILES[*]}"
echo "CONNECT_SERVICE=${CONNECT_SERVICE}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "DEBEZIUM_CONNECT_URL=${DEBEZIUM_CONNECT_URL}"
echo "DEBEZIUM_CONNECTOR_NAME=${DEBEZIUM_CONNECTOR_NAME}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "DEBEZIUM_TOPIC_PREFIX=${DEBEZIUM_TOPIC_PREFIX}"
echo "DEBEZIUM_SCHEMA_HISTORY_TOPIC=${DEBEZIUM_SCHEMA_HISTORY_TOPIC}"
echo

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to register Debezium connector against real application database: ${PERF_DB_NAME}"
fi

for service in "${MYSQL_SERVICE}" "${KAFKA_SERVICE}" "${CONNECT_SERVICE}"; do
  if ! compose ps --services --filter status=running | grep -qx "${service}"; then
    fail "service \"${service}\" is not running"
  fi
done

echo "### Ensure Debezium schema history topic accepts keyless records"
compose exec -T "${KAFKA_SERVICE}" \
  kafka-configs \
    --bootstrap-server "${KAFKA_SERVICE}:29092" \
    --alter \
    --entity-type topics \
    --entity-name "${DEBEZIUM_SCHEMA_HISTORY_TOPIC}" \
    --add-config cleanup.policy=delete >/dev/null
echo "schema_history_cleanup_policy=delete"
echo

echo "### Verify MySQL binlog settings"
binlog_summary="$(
  compose exec -T -e MYSQL_PWD="${DEBEZIUM_DB_PASSWORD}" "${MYSQL_SERVICE}" mysql \
    -u"${DEBEZIUM_DB_USER}" \
    -Nse "
      SELECT CONCAT(@@log_bin, '|', @@binlog_format, '|', @@binlog_row_image, '|', @@server_id);
    "
)"
echo "mysql_binlog_summary=${binlog_summary}"

IFS='|' read -r log_bin binlog_format binlog_row_image mysql_server_id <<< "${binlog_summary}"
[[ "${log_bin}" == "1" ]] || fail "MySQL log_bin must be enabled for Debezium"
[[ "${binlog_format}" == "ROW" ]] || fail "MySQL binlog_format must be ROW"
[[ "${binlog_row_image}" == "FULL" ]] || fail "MySQL binlog_row_image must be FULL"
[[ "${mysql_server_id}" != "${DEBEZIUM_CONNECTOR_DATABASE_SERVER_ID}" ]] || fail "Debezium connector database.server.id must differ from MySQL server_id"

echo
echo "### Wait for Debezium Connect REST"
for ((i = 1; i <= DEBEZIUM_WAIT_SECONDS; i++)); do
  if curl -fsS "${DEBEZIUM_CONNECT_URL}/connectors" >/dev/null; then
    echo "debezium_connect_ready_after=${i}s"
    break
  fi
  if (( i == DEBEZIUM_WAIT_SECONDS )); then
    fail "Debezium Connect REST did not become ready"
  fi
  sleep 1
done

connector_config_file="$(mktemp)"
cat > "${connector_config_file}" <<JSON
{
  "connector.class": "io.debezium.connector.mysql.MySqlConnector",
  "tasks.max": "1",
  "database.hostname": "${DEBEZIUM_DB_HOST}",
  "database.port": "${DEBEZIUM_DB_PORT}",
  "database.user": "${DEBEZIUM_DB_USER}",
  "database.password": "${DEBEZIUM_DB_PASSWORD}",
  "database.server.id": "${DEBEZIUM_CONNECTOR_DATABASE_SERVER_ID}",
  "topic.prefix": "${DEBEZIUM_TOPIC_PREFIX}",
  "database.include.list": "${PERF_DB_NAME}",
  "table.include.list": "${PERF_DB_NAME}.outbox_events",
  "include.schema.changes": "false",
  "snapshot.mode": "schema_only",
  "skipped.operations": "u,d",
  "tombstones.on.delete": "false",
  "schema.history.internal.kafka.bootstrap.servers": "kafka:29092",
  "schema.history.internal.kafka.topic": "${DEBEZIUM_SCHEMA_HISTORY_TOPIC}",
  "key.converter": "org.apache.kafka.connect.json.JsonConverter",
  "key.converter.schemas.enable": "false",
  "value.converter": "org.apache.kafka.connect.json.JsonConverter",
  "value.converter.schemas.enable": "false",
  "transforms": "outbox",
  "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
  "transforms.outbox.route.by.field": "topic",
  "transforms.outbox.route.topic.replacement": "\${routedByValue}",
  "transforms.outbox.table.field.event.id": "id",
  "transforms.outbox.table.field.event.key": "aggregate_id",
  "transforms.outbox.table.field.event.type": "event_type",
  "transforms.outbox.table.field.event.payload": "payload",
  "transforms.outbox.table.field.event.timestamp": "created_at",
  "transforms.outbox.table.expand.json.payload": "true",
  "transforms.outbox.table.fields.additional.placement": "schema_version:envelope:schemaVersion,aggregate_type:envelope:aggregateType,aggregate_id:envelope:aggregateId,event_type:envelope:eventType,topic:envelope:topic"
}
JSON

echo
echo "### Register Debezium connector"
curl -fsS \
  -X PUT \
  -H "Content-Type: application/json" \
  --data-binary "@${connector_config_file}" \
  "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/config" >/dev/null
rm -f "${connector_config_file}"

echo "connector_registered=true"
echo

echo "### Wait for connector RUNNING"
for ((i = 1; i <= DEBEZIUM_WAIT_SECONDS; i++)); do
  status_json="$(curl -fsS "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/status" 2>/dev/null || true)"

  if [[ -z "${status_json}" ]]; then
    echo "connector_status_wait_elapsed=${i}s status=not_found_or_not_ready"
    sleep 1
    continue
  fi

  echo "connector_status_wait_elapsed=${i}s ${status_json}"

  running_state_count="$(echo "${status_json}" | grep -Eo '"state"[[:space:]]*:[[:space:]]*"RUNNING"' | wc -l | tr -d ' ')"
  if [[ "${running_state_count}" -ge 2 ]]; then
    echo
    echo "Debezium outbox connector is RUNNING."
    exit 0
  fi

  sleep 1
done

curl -fsS "${DEBEZIUM_CONNECT_URL}/connectors/${DEBEZIUM_CONNECTOR_NAME}/status" >&2 || true
fail "Debezium connector did not reach RUNNING state"
