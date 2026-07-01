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
EMAIL_SEND_TOPIC="${EMAIL_SEND_TOPIC:-email.send}"
KAFKA_IDEMPOTENCY_SMOKE_RUN_ID="${KAFKA_IDEMPOTENCY_SMOKE_RUN_ID:-kafka-idempotency-smoke-$(date +%Y%m%d%H%M%S)}"
KAFKA_IDEMPOTENCY_EVENT_ID="${KAFKA_IDEMPOTENCY_EVENT_ID:-$((8100000000 + $(date +%s) % 1000000000))}"
KAFKA_IDEMPOTENCY_WAIT_SECONDS="${KAFKA_IDEMPOTENCY_WAIT_SECONDS:-45}"

cd "${ROOT_DIR}"

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

publish_message() {
  local topic="$1"
  local key="$2"
  local message="$3"

  printf '%s|%s\n' "${key}" "${message}" \
    | compose exec -T "${KAFKA_SERVICE}" kafka-console-producer \
        --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
        --topic "${topic}" \
        --property parse.key=true \
        --property key.separator='|'
}

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to run Kafka idempotency smoke against real database: ${PERF_DB_NAME}"
fi

if ! compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  fail "service \"${MYSQL_SERVICE}\" is not running"
fi

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "EMAIL_SEND_TOPIC=${EMAIL_SEND_TOPIC}"
echo "KAFKA_IDEMPOTENCY_SMOKE_RUN_ID=${KAFKA_IDEMPOTENCY_SMOKE_RUN_ID}"
echo "KAFKA_IDEMPOTENCY_EVENT_ID=${KAFKA_IDEMPOTENCY_EVENT_ID}"
echo

mysql_exec -Nse "
  DELETE FROM processed_kafka_events
  WHERE consumer_name = 'email-send'
    AND event_id = ${KAFKA_IDEMPOTENCY_EVENT_ID};
"

email_message="$(
  jq -cn \
    --argjson eventId "${KAFKA_IDEMPOTENCY_EVENT_ID}" \
    --arg topic "${EMAIL_SEND_TOPIC}" \
    --arg smokeRunId "${KAFKA_IDEMPOTENCY_SMOKE_RUN_ID}" \
    '{
      eventId: $eventId,
      aggregateType: "EMAIL",
      aggregateId: $eventId,
      eventType: "EMAIL_SEND_REQUESTED",
      topic: $topic,
      payload: {
        to: "user@example.com",
        subject: ("Kafka idempotency smoke " + $smokeRunId),
        text: "Kafka idempotency smoke email body",
        html: null,
        smokeRunId: $smokeRunId
      }
    }'
)"

echo "### Publish duplicated email.send messages"
publish_message "${EMAIL_SEND_TOPIC}" "EMAIL:${KAFKA_IDEMPOTENCY_EVENT_ID}" "${email_message}"
publish_message "${EMAIL_SEND_TOPIC}" "EMAIL:${KAFKA_IDEMPOTENCY_EVENT_ID}" "${email_message}"

processed_count=0
for ((i = 1; i <= KAFKA_IDEMPOTENCY_WAIT_SECONDS; i++)); do
  processed_count="$(
    mysql_exec -Nse "
      SELECT COUNT(*)
      FROM processed_kafka_events
      WHERE consumer_name = 'email-send'
        AND event_id = ${KAFKA_IDEMPOTENCY_EVENT_ID};
    "
  )"

  echo "idempotency_wait_elapsed=${i}s processed_count=${processed_count}"

  if [[ "${processed_count}" == "1" ]]; then
    break
  fi
  sleep 1
done

if [[ "${processed_count}" != "1" ]]; then
  compose logs --since=5m backend | grep -Ei 'Kafka|duplicate|consumer|error|exception' || true
  fail "expected one processed_kafka_events row, got ${processed_count}"
fi

duplicate_log_count="$(
  compose logs --since=5m backend \
    | grep -F "Kafka duplicate event skipped" \
    | grep -F "eventId=${KAFKA_IDEMPOTENCY_EVENT_ID}" \
    | wc -l \
    | tr -d ' '
)"

echo
echo "### Kafka Duplicate Replay Idempotency Summary"
echo "event_id=${KAFKA_IDEMPOTENCY_EVENT_ID}"
echo "consumer_name=email-send"
echo "processed_count=${processed_count}"
echo "duplicate_skip_log_count=${duplicate_log_count}"
echo "Kafka duplicate replay idempotency smoke completed."
