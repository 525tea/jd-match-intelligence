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
KAFKA_DLQ_TOPIC="${KAFKA_DLQ_TOPIC:-${EMAIL_SEND_TOPIC}.dlq}"
DLQ_BASE_URL="${DLQ_BASE_URL:-http://localhost:8080}"
ADMIN_ACCESS_TOKEN="${ADMIN_ACCESS_TOKEN:-}"
KAFKA_DLQ_SMOKE_RUN_ID="${KAFKA_DLQ_SMOKE_RUN_ID:-kafka-dlq-poison-$(date +%Y%m%d%H%M%S)}"
KAFKA_DLQ_EVENT_ID="${KAFKA_DLQ_EVENT_ID:-$((8300000000 + $(date +%s) % 1000000000))}"
KAFKA_DLQ_WAIT_SECONDS="${KAFKA_DLQ_WAIT_SECONDS:-60}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/kafka/$(date +%y%m%d)_kafka_failure_recovery}"

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
  fail "Refusing to run Kafka DLQ smoke against real database: ${PERF_DB_NAME}"
fi

if [[ -z "${ADMIN_ACCESS_TOKEN}" ]]; then
  fail "ADMIN_ACCESS_TOKEN is required to verify /admin/dlq/messages/{id}/retry"
fi

if ! compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  fail "service \"${MYSQL_SERVICE}\" is not running"
fi

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

summary_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_DLQ_SMOKE_RUN_ID}_dlq_poison_retry.txt"
source_key="EMAIL:${KAFKA_DLQ_EVENT_ID}"

{
  echo "ROOT_DIR=${ROOT_DIR}"
  echo "ENV_FILE=${ENV_FILE}"
  echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
  echo "EMAIL_SEND_TOPIC=${EMAIL_SEND_TOPIC}"
  echo "KAFKA_DLQ_TOPIC=${KAFKA_DLQ_TOPIC}"
  echo "KAFKA_DLQ_SMOKE_RUN_ID=${KAFKA_DLQ_SMOKE_RUN_ID}"
  echo "KAFKA_DLQ_EVENT_ID=${KAFKA_DLQ_EVENT_ID}"
  echo "DLQ_BASE_URL=${DLQ_BASE_URL}"
  echo "ADMIN_ACCESS_TOKEN=provided"
  echo
} | tee "${summary_file}"

echo "### Ensure DLQ Kafka topic" | tee -a "${summary_file}"
KAFKA_EXPECTED_TOPICS="${KAFKA_DLQ_TOPIC}" \
bash performance/events/ensure-kafka-topics.sh | tee -a "${summary_file}"

mysql_exec -Nse "
  DELETE FROM processed_kafka_events
  WHERE consumer_name = 'email-send'
    AND event_id = ${KAFKA_DLQ_EVENT_ID};
"

poison_message="$(
  jq -cn \
    --argjson eventId "${KAFKA_DLQ_EVENT_ID}" \
    --arg topic "${EMAIL_SEND_TOPIC}" \
    --arg smokeRunId "${KAFKA_DLQ_SMOKE_RUN_ID}" \
    '{
      schemaVersion: 1,
      eventId: $eventId,
      aggregateType: "EMAIL",
      aggregateId: $eventId,
      eventType: "EMAIL_SEND_REQUESTED",
      topic: $topic,
      payload: {
        to: "user@example.com",
        subject: ("Kafka DLQ poison smoke " + $smokeRunId),
        html: null,
        smokeRunId: $smokeRunId
      }
    }'
)"

echo "### Publish poison email.send message" | tee -a "${summary_file}"
publish_message "${EMAIL_SEND_TOPIC}" "${source_key}" "${poison_message}"

dlq_id=""
for ((i = 1; i <= KAFKA_DLQ_WAIT_SECONDS; i++)); do
  dlq_id="$(
    mysql_exec -Nse "
      SELECT id
      FROM dlq_messages
      WHERE source_topic = '${EMAIL_SEND_TOPIC}'
        AND source_key = '${source_key}'
      ORDER BY id DESC
      LIMIT 1;
    "
  )"
  echo "dlq_wait_elapsed=${i}s dlq_id=${dlq_id:-none}" | tee -a "${summary_file}"
  if [[ -n "${dlq_id}" ]]; then
    break
  fi
  sleep 1
done

if [[ -z "${dlq_id}" ]]; then
  compose logs --since=5m backend | grep -Ei 'DLQ|Kafka|email send|exception|error' || true
  fail "expected poison message to be persisted into dlq_messages"
fi

dlq_status_before="$(
  mysql_exec -Nse "SELECT status FROM dlq_messages WHERE id = ${dlq_id};"
)"
if [[ "${dlq_status_before}" != "PENDING" ]]; then
  fail "expected initial DLQ status PENDING, got ${dlq_status_before}"
fi

detail_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_DLQ_SMOKE_RUN_ID}_dlq_detail.json"
retry_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_DLQ_SMOKE_RUN_ID}_dlq_retry_response.json"

echo
echo "### Verify DLQ detail API" | tee -a "${summary_file}"
curl -fsS \
  -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  "${DLQ_BASE_URL}/admin/dlq/messages/${dlq_id}" \
  -o "${detail_file}"
jq '.' "${detail_file}" | tee -a "${summary_file}"

echo
echo "### Retry DLQ message by id" | tee -a "${summary_file}"
curl -fsS \
  -X POST \
  -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}" \
  "${DLQ_BASE_URL}/admin/dlq/messages/${dlq_id}/retry" \
  -o "${retry_file}"
jq '.' "${retry_file}" | tee -a "${summary_file}"

dlq_status_after="$(
  mysql_exec -Nse "SELECT CONCAT(status, '|', retry_count) FROM dlq_messages WHERE id = ${dlq_id};"
)"

if [[ "${dlq_status_after}" != "RETRIED|1" ]]; then
  fail "expected retried DLQ row to be RETRIED|1, got ${dlq_status_after}"
fi

redlq_count=0
for ((i = 1; i <= KAFKA_DLQ_WAIT_SECONDS; i++)); do
  redlq_count="$(
    mysql_exec -Nse "
      SELECT COUNT(*)
      FROM dlq_messages
      WHERE source_topic = '${EMAIL_SEND_TOPIC}'
        AND source_key = '${source_key}';
    "
  )"
  echo "retry_redlq_wait_elapsed=${i}s dlq_row_count_for_source_key=${redlq_count}" | tee -a "${summary_file}"
  if (( redlq_count >= 2 )); then
    break
  fi
  sleep 1
done

{
  echo
  echo "### Kafka DLQ Poison Retry Summary"
  echo "event_id=${KAFKA_DLQ_EVENT_ID}"
  echo "source_key=${source_key}"
  echo "initial_dlq_message_id=${dlq_id}"
  echo "initial_dlq_status=${dlq_status_before}"
  echo "retried_dlq_status=${dlq_status_after}"
  echo "dlq_row_count_for_source_key=${redlq_count}"
  echo "detail_file=${detail_file}"
  echo "retry_file=${retry_file}"
  echo "summary_file=${summary_file}"
  echo "Kafka DLQ poison retry smoke completed."
} | tee -a "${summary_file}"
