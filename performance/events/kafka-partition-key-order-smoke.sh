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
KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
KAFKA_PARTITION_SMOKE_TOPIC="${KAFKA_PARTITION_SMOKE_TOPIC:-email.send}"
KAFKA_PARTITION_SMOKE_RUN_ID="${KAFKA_PARTITION_SMOKE_RUN_ID:-kafka-partition-key-$(date +%Y%m%d%H%M%S)}"
KAFKA_PARTITION_SMOKE_COUNT="${KAFKA_PARTITION_SMOKE_COUNT:-5}"
KAFKA_PARTITION_SMOKE_EVENT_ID="${KAFKA_PARTITION_SMOKE_EVENT_ID:-$((8400000000 + $(date +%s) % 1000000000))}"
KAFKA_CONSUMER_TIMEOUT_MS="${KAFKA_CONSUMER_TIMEOUT_MS:-15000}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/kafka/$(date +%y%m%d)_kafka_failure_recovery}"

cd "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

if (( KAFKA_PARTITION_SMOKE_COUNT < 2 )); then
  fail "KAFKA_PARTITION_SMOKE_COUNT must be at least 2"
fi

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

summary_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_PARTITION_SMOKE_RUN_ID}_partition_key_order.txt"
consumer_output_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_PARTITION_SMOKE_RUN_ID}_partition_key_consumer_output.txt"
offsets_before_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_PARTITION_SMOKE_RUN_ID}_partition_offsets_before.txt"
offsets_after_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_PARTITION_SMOKE_RUN_ID}_partition_offsets_after.txt"
source_key="EMAIL:${KAFKA_PARTITION_SMOKE_EVENT_ID}"

{
  echo "ROOT_DIR=${ROOT_DIR}"
  echo "ENV_FILE=${ENV_FILE}"
  echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
  echo "KAFKA_PARTITION_SMOKE_TOPIC=${KAFKA_PARTITION_SMOKE_TOPIC}"
  echo "KAFKA_PARTITION_SMOKE_RUN_ID=${KAFKA_PARTITION_SMOKE_RUN_ID}"
  echo "KAFKA_PARTITION_SMOKE_EVENT_ID=${KAFKA_PARTITION_SMOKE_EVENT_ID}"
  echo "KAFKA_PARTITION_SMOKE_COUNT=${KAFKA_PARTITION_SMOKE_COUNT}"
  echo "source_key=${source_key}"
  echo
} | tee "${summary_file}"

compose exec -T "${KAFKA_SERVICE}" \
  kafka-get-offsets \
    --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
    --topic "${KAFKA_PARTITION_SMOKE_TOPIC}" \
  > "${offsets_before_file}"

awk -v count="${KAFKA_PARTITION_SMOKE_COUNT}" \
    -v event_id="${KAFKA_PARTITION_SMOKE_EVENT_ID}" \
    -v topic="${KAFKA_PARTITION_SMOKE_TOPIC}" \
    -v run_id="${KAFKA_PARTITION_SMOKE_RUN_ID}" \
    -v source_key="${source_key}" '
  BEGIN {
    for (i = 1; i <= count; i++) {
      printf "%s|{\"schemaVersion\":1,\"eventId\":%d,\"aggregateType\":\"EMAIL\",\"aggregateId\":%d,\"eventType\":\"EMAIL_SEND_REQUESTED\",\"topic\":\"%s\",\"payload\":{\"to\":\"user@example.com\",\"subject\":\"Kafka partition key smoke %s #%d\",\"text\":\"Kafka partition key smoke email body\",\"html\":null,\"smokeRunId\":\"%s\",\"sequence\":%d}}\n",
        source_key, event_id + i, event_id, topic, run_id, i, run_id, i
    }
  }
' | compose exec -T "${KAFKA_SERVICE}" kafka-console-producer \
      --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
      --topic "${KAFKA_PARTITION_SMOKE_TOPIC}" \
      --property parse.key=true \
      --property key.separator='|'

compose exec -T "${KAFKA_SERVICE}" \
  kafka-get-offsets \
    --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
    --topic "${KAFKA_PARTITION_SMOKE_TOPIC}" \
  > "${offsets_after_file}"

while IFS=: read -r topic partition after_offset; do
  [[ "${topic}" != "${KAFKA_PARTITION_SMOKE_TOPIC}" ]] && continue
  before_offset="$(
    awk -F: -v topic="${topic}" -v partition="${partition}" '
      $1 == topic && $2 == partition { print $3; exit }
    ' "${offsets_before_file}"
  )"
  before_offset="${before_offset:-0}"
  message_count=$((after_offset - before_offset))
  if (( message_count <= 0 )); then
    continue
  fi
  compose exec -T "${KAFKA_SERVICE}" \
    kafka-console-consumer \
      --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
      --topic "${KAFKA_PARTITION_SMOKE_TOPIC}" \
      --partition "${partition}" \
      --offset "${before_offset}" \
      --max-messages "${message_count}" \
      --property print.partition=true \
      --property print.key=true \
      --property key.separator='|' \
    >> "${consumer_output_file}" 2>&1 || true
done < "${offsets_after_file}"

matched_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_PARTITION_SMOKE_RUN_ID}_partition_key_matched.txt"
grep -F "\"smokeRunId\":\"${KAFKA_PARTITION_SMOKE_RUN_ID}\"" "${consumer_output_file}" > "${matched_file}" || true

matched_count="$(wc -l < "${matched_file}" | tr -d ' ')"
if [[ "${matched_count}" != "${KAFKA_PARTITION_SMOKE_COUNT}" ]]; then
  cat "${consumer_output_file}" >&2
  fail "expected ${KAFKA_PARTITION_SMOKE_COUNT} partition smoke messages, got ${matched_count}"
fi

partition_count="$(
  awk '
    {
      partition = ""
      if (match($0, /^Partition:([0-9]+)/)) {
        partition = substr($0, RSTART + 10, RLENGTH - 10)
      }
      if (partition != "") {
        partitions[partition] = 1
      }
    }
    END {
      count = 0
      for (partition in partitions) {
        count++
      }
      print count + 0
    }
  ' "${matched_file}"
)"

if [[ "${partition_count}" != "1" ]]; then
  cat "${matched_file}" >&2
  fail "expected all same-key messages to land on one partition, got ${partition_count} partitions"
fi

sequence_list="$(
  sed -E 's/.*"sequence":([0-9]+).*/\1/' "${matched_file}" | paste -sd ',' -
)"
expected_sequence="$(seq -s ',' 1 "${KAFKA_PARTITION_SMOKE_COUNT}")"

if [[ "${sequence_list}" != "${expected_sequence}" ]]; then
  cat "${matched_file}" >&2
  fail "expected sequence order ${expected_sequence}, got ${sequence_list}"
fi

{
  echo
  echo "### Kafka Partition Key / Order Summary"
  echo "topic=${KAFKA_PARTITION_SMOKE_TOPIC}"
  echo "source_key=${source_key}"
  echo "matched_count=${matched_count}"
  echo "partition_count=${partition_count}"
  echo "sequence_list=${sequence_list}"
  echo "offsets_before_file=${offsets_before_file}"
  echo "offsets_after_file=${offsets_after_file}"
  echo "consumer_output_file=${consumer_output_file}"
  echo "matched_file=${matched_file}"
  echo "summary_file=${summary_file}"
  echo "Kafka partition key/order smoke completed."
} | tee -a "${summary_file}"
