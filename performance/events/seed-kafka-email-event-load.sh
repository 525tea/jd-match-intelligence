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
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
KAFKA_EVENT_LOAD_RUN_ID="${KAFKA_EVENT_LOAD_RUN_ID:-kafka-event-load-$(date +%Y%m%d%H%M%S)}"
KAFKA_EVENT_LOAD_COUNT="${KAFKA_EVENT_LOAD_COUNT:-60000}"
KAFKA_EVENT_LOAD_BATCH_SIZE="${KAFKA_EVENT_LOAD_BATCH_SIZE:-1000}"
KAFKA_EVENT_LOAD_TOPIC="${KAFKA_EVENT_LOAD_TOPIC:-email.send}"
KAFKA_EVENT_LOAD_START_AGGREGATE_ID="${KAFKA_EVENT_LOAD_START_AGGREGATE_ID:-$((9000000000 + $(date +%s) % 100000000))}"

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

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to seed Kafka event load against real database: ${PERF_DB_NAME}"
fi

if [[ ! "${KAFKA_EVENT_LOAD_RUN_ID}" =~ ^[A-Za-z0-9._:-]+$ ]]; then
  fail "KAFKA_EVENT_LOAD_RUN_ID contains unsupported characters: ${KAFKA_EVENT_LOAD_RUN_ID}"
fi

if (( KAFKA_EVENT_LOAD_COUNT <= 0 )); then
  fail "KAFKA_EVENT_LOAD_COUNT must be positive"
fi

if (( KAFKA_EVENT_LOAD_BATCH_SIZE <= 0 || KAFKA_EVENT_LOAD_BATCH_SIZE > 1000 )); then
  fail "KAFKA_EVENT_LOAD_BATCH_SIZE must be between 1 and 1000"
fi

if ! compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  fail "service \"${MYSQL_SERVICE}\" is not running"
fi

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "KAFKA_EVENT_LOAD_RUN_ID=${KAFKA_EVENT_LOAD_RUN_ID}"
echo "KAFKA_EVENT_LOAD_COUNT=${KAFKA_EVENT_LOAD_COUNT}"
echo "KAFKA_EVENT_LOAD_BATCH_SIZE=${KAFKA_EVENT_LOAD_BATCH_SIZE}"
echo "KAFKA_EVENT_LOAD_TOPIC=${KAFKA_EVENT_LOAD_TOPIC}"
echo "KAFKA_EVENT_LOAD_START_AGGREGATE_ID=${KAFKA_EVENT_LOAD_START_AGGREGATE_ID}"
echo

inserted=0
batch_index=0

while (( inserted < KAFKA_EVENT_LOAD_COUNT )); do
  remaining=$((KAFKA_EVENT_LOAD_COUNT - inserted))
  current_batch="${KAFKA_EVENT_LOAD_BATCH_SIZE}"
  if (( remaining < current_batch )); then
    current_batch="${remaining}"
  fi

  aggregate_offset=$((batch_index * KAFKA_EVENT_LOAD_BATCH_SIZE))
  mysql_exec <<SQL
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
SELECT
  'EMAIL',
  ${KAFKA_EVENT_LOAD_START_AGGREGATE_ID} + ${aggregate_offset} + seq.n,
  'EMAIL_SEND_REQUESTED',
  JSON_OBJECT(
    'to', 'user@example.com',
    'subject', CONCAT('Kafka event load ', '${KAFKA_EVENT_LOAD_RUN_ID}', ' #', seq.n + ${aggregate_offset}),
    'text', 'Kafka event load body',
    'html', NULL,
    'smokeRunId', '${KAFKA_EVENT_LOAD_RUN_ID}'
  ),
  '${KAFKA_EVENT_LOAD_TOPIC}',
  'PENDING',
  0,
  NULL,
  NOW(6),
  NULL
FROM (
  SELECT ones.n + tens.n * 10 + hundreds.n * 100 + 1 AS n
  FROM
    (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) ones
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) tens
    CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) hundreds
) seq
WHERE seq.n <= ${current_batch};
SQL

  inserted=$((inserted + current_batch))
  batch_index=$((batch_index + 1))
  echo "seeded_events=${inserted}/${KAFKA_EVENT_LOAD_COUNT}"
done

echo
echo "### Kafka Event Load Seed Summary"
echo "kafka_event_load_run_id=${KAFKA_EVENT_LOAD_RUN_ID}"
echo "seeded_event_count=${inserted}"
echo "topic=${KAFKA_EVENT_LOAD_TOPIC}"
echo "status=PENDING"
