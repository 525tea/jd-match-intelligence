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
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
OUTBOX_SMOKE_TOPIC="${OUTBOX_SMOKE_TOPIC:-job.created}"
OUTBOX_SMOKE_RUN_ID="${OUTBOX_SMOKE_RUN_ID:-outbox-kafka-smoke-$(date +%Y%m%d%H%M%S)}"
OUTBOX_SMOKE_AGGREGATE_ID="${OUTBOX_SMOKE_AGGREGATE_ID:-$((7000000000 + $(date +%s) % 1000000000))}"
OUTBOX_SMOKE_WAIT_SECONDS="${OUTBOX_SMOKE_WAIT_SECONDS:-30}"
KAFKA_CONSUMER_TIMEOUT_MS="${KAFKA_CONSUMER_TIMEOUT_MS:-10000}"

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

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "COMPOSE_FILES=${COMPOSE_FILES[*]}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "OUTBOX_SMOKE_TOPIC=${OUTBOX_SMOKE_TOPIC}"
echo "OUTBOX_SMOKE_RUN_ID=${OUTBOX_SMOKE_RUN_ID}"
echo "OUTBOX_SMOKE_AGGREGATE_ID=${OUTBOX_SMOKE_AGGREGATE_ID}"
echo "OUTBOX_SMOKE_WAIT_SECONDS=${OUTBOX_SMOKE_WAIT_SECONDS}"
echo "KAFKA_CONSUMER_TIMEOUT_MS=${KAFKA_CONSUMER_TIMEOUT_MS}"
echo

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to run outbox Kafka smoke against real database: ${PERF_DB_NAME}"
fi

if [[ ! "${OUTBOX_SMOKE_RUN_ID}" =~ ^[A-Za-z0-9._:-]+$ ]]; then
  fail "OUTBOX_SMOKE_RUN_ID contains unsupported characters: ${OUTBOX_SMOKE_RUN_ID}"
fi

if ! compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  fail "service \"${MYSQL_SERVICE}\" is not running"
fi

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

if ! curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
  fail "backend health is not UP"
fi

echo "### Insert pending outbox smoke event"
smoke_event_id="$(
  mysql_exec -Nse "
    INSERT INTO outbox_events (
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
      'JOB',
      ${OUTBOX_SMOKE_AGGREGATE_ID},
      'JOB_CREATED',
      JSON_OBJECT(
        'smokeRunId', '${OUTBOX_SMOKE_RUN_ID}',
        'jobId', ${OUTBOX_SMOKE_AGGREGATE_ID},
        'title', 'Sample backend engineer',
        'companyName', 'Sample Company'
      ),
      '${OUTBOX_SMOKE_TOPIC}',
      'PENDING',
      0,
      NULL,
      NOW(6),
      NULL
    );
    SELECT LAST_INSERT_ID();
  "
)"

echo "outbox_smoke_event_id=${smoke_event_id}"
echo

echo "### Wait for outbox relay publish"
outbox_status=""
outbox_last_error=""
for ((i = 1; i <= OUTBOX_SMOKE_WAIT_SECONDS; i++)); do
  outbox_row="$(
    mysql_exec -Nse "
      SELECT CONCAT(status, '|', COALESCE(last_error, ''))
      FROM outbox_events
      WHERE id = ${smoke_event_id};
    "
  )"
  outbox_status="${outbox_row%%|*}"
  outbox_last_error="${outbox_row#*|}"

  echo "relay_wait_elapsed=${i}s outbox_event_status=${outbox_status}"

  if [[ "${outbox_status}" == "PUBLISHED" ]]; then
    break
  fi

  sleep 1
done

if [[ "${outbox_status}" != "PUBLISHED" ]]; then
  echo "outbox_last_error=${outbox_last_error}" >&2
  fail "outbox smoke event was not published"
fi

echo
echo "### Verify Kafka message"
consumer_output_file="$(mktemp)"
consumer_error_file="$(mktemp)"

compose exec -T "${KAFKA_SERVICE}" \
  kafka-console-consumer \
    --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
    --topic "${OUTBOX_SMOKE_TOPIC}" \
    --from-beginning \
    --timeout-ms "${KAFKA_CONSUMER_TIMEOUT_MS}" \
    --property print.key=true \
    --property key.separator='|' \
  > "${consumer_output_file}" 2> "${consumer_error_file}" || true

kafka_message="$(
  grep -F "\"eventId\":${smoke_event_id}" "${consumer_output_file}" | tail -1 || true
)"

if [[ -z "${kafka_message}" ]]; then
  echo "Kafka consumer stderr:" >&2
  cat "${consumer_error_file}" >&2
  fail "Kafka message for outbox event ${smoke_event_id} was not found in topic ${OUTBOX_SMOKE_TOPIC}"
fi

echo "kafka_message_found=true"
echo "kafka_message=${kafka_message}"
echo

echo "### Outbox Kafka Publish Smoke Summary"
echo "outbox_smoke_event_id=${smoke_event_id}"
echo "outbox_event_status=${outbox_status}"
echo "kafka_topic=${OUTBOX_SMOKE_TOPIC}"
echo "kafka_message_found=true"
echo
echo "Outbox Kafka publish smoke completed."
