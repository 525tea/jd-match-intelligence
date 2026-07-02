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
KAFKA_DIRECT_BURST_TOPIC="${KAFKA_DIRECT_BURST_TOPIC:-email.send}"
KAFKA_DIRECT_BURST_RUN_ID="${KAFKA_DIRECT_BURST_RUN_ID:-kafka-direct-burst-$(date +%Y%m%d%H%M%S)}"
KAFKA_DIRECT_BURST_COUNT="${KAFKA_DIRECT_BURST_COUNT:-10000}"
KAFKA_DIRECT_BURST_START_EVENT_ID="${KAFKA_DIRECT_BURST_START_EVENT_ID:-$((8200000000 + $(date +%s) % 1000000000))}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/kafka/$(date +%y%m%d)_kafka_consumer_latency_lag}"

cd "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

if (( KAFKA_DIRECT_BURST_COUNT <= 0 )); then
  fail "KAFKA_DIRECT_BURST_COUNT must be positive"
fi

if (( KAFKA_DIRECT_BURST_COUNT > 50000 )); then
  fail "KAFKA_DIRECT_BURST_COUNT is capped at 50000 for a single safe burst"
fi

if [[ ! "${KAFKA_DIRECT_BURST_RUN_ID}" =~ ^[A-Za-z0-9._:-]+$ ]]; then
  fail "KAFKA_DIRECT_BURST_RUN_ID contains unsupported characters: ${KAFKA_DIRECT_BURST_RUN_ID}"
fi

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

summary_file="${ARTIFACT_DIR}/$(date +%Y%m%d%H%M%S)_${KAFKA_DIRECT_BURST_RUN_ID}_direct_topic_burst.txt"

{
  echo "ROOT_DIR=${ROOT_DIR}"
  echo "ENV_FILE=${ENV_FILE}"
  echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
  echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
  echo "KAFKA_DIRECT_BURST_TOPIC=${KAFKA_DIRECT_BURST_TOPIC}"
  echo "KAFKA_DIRECT_BURST_RUN_ID=${KAFKA_DIRECT_BURST_RUN_ID}"
  echo "KAFKA_DIRECT_BURST_COUNT=${KAFKA_DIRECT_BURST_COUNT}"
  echo "KAFKA_DIRECT_BURST_START_EVENT_ID=${KAFKA_DIRECT_BURST_START_EVENT_ID}"
  echo
} | tee "${summary_file}"

awk -v count="${KAFKA_DIRECT_BURST_COUNT}" \
    -v start_event_id="${KAFKA_DIRECT_BURST_START_EVENT_ID}" \
    -v topic="${KAFKA_DIRECT_BURST_TOPIC}" \
    -v run_id="${KAFKA_DIRECT_BURST_RUN_ID}" '
  BEGIN {
    for (i = 0; i < count; i++) {
      event_id = start_event_id + i
      key = "EMAIL:" event_id
      printf "%s|{\"schemaVersion\":1,\"eventId\":%d,\"aggregateType\":\"EMAIL\",\"aggregateId\":%d,\"eventType\":\"EMAIL_SEND_REQUESTED\",\"topic\":\"%s\",\"payload\":{\"to\":\"user@example.com\",\"subject\":\"Kafka direct burst %s #%d\",\"text\":\"Kafka direct burst email body\",\"html\":null,\"smokeRunId\":\"%s\"}}\n",
        key, event_id, event_id, topic, run_id, i + 1, run_id
    }
  }
' | compose exec -T "${KAFKA_SERVICE}" kafka-console-producer \
      --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
      --topic "${KAFKA_DIRECT_BURST_TOPIC}" \
      --property parse.key=true \
      --property key.separator='|'

{
  echo
  echo "### Kafka Direct Topic Burst Summary"
  echo "topic=${KAFKA_DIRECT_BURST_TOPIC}"
  echo "run_id=${KAFKA_DIRECT_BURST_RUN_ID}"
  echo "published_message_count=${KAFKA_DIRECT_BURST_COUNT}"
  echo "start_event_id=${KAFKA_DIRECT_BURST_START_EVENT_ID}"
  echo "end_event_id=$((KAFKA_DIRECT_BURST_START_EVENT_ID + KAFKA_DIRECT_BURST_COUNT - 1))"
  echo "summary_file=${summary_file}"
} | tee -a "${summary_file}"
