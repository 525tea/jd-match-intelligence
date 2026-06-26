#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
KAFKA_EXPECTED_TOPICS="${KAFKA_EXPECTED_TOPICS:-job.created application.events email.send es.index}"
KAFKA_EXPECTED_PARTITIONS="${KAFKA_EXPECTED_PARTITIONS:-3}"
KAFKA_EXPECTED_REPLICATION_FACTOR="${KAFKA_EXPECTED_REPLICATION_FACTOR:-1}"

cd "${ROOT_DIR}"

echo "ROOT_DIR=${ROOT_DIR}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "KAFKA_EXPECTED_TOPICS=${KAFKA_EXPECTED_TOPICS}"
echo "KAFKA_EXPECTED_PARTITIONS=${KAFKA_EXPECTED_PARTITIONS}"
echo "KAFKA_EXPECTED_REPLICATION_FACTOR=${KAFKA_EXPECTED_REPLICATION_FACTOR}"
echo

if ! docker compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  echo "service \"${KAFKA_SERVICE}\" is not running" >&2
  echo "Start the staging performance stack first:" >&2
  echo "  REQUIRED_PORTS=\"\" bash performance/deploy/staging-performance-up.sh" >&2
  exit 1
fi

echo "### Kafka topic list"
topic_list="$(docker compose exec -T "${KAFKA_SERVICE}" \
  kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --list | tr -d '\r')"
echo "${topic_list}"
echo

for topic in ${KAFKA_EXPECTED_TOPICS}; do
  if ! grep -qx "${topic}" <<< "${topic_list}"; then
    echo "Expected Kafka topic '${topic}' was not found." >&2
    exit 1
  fi

  echo "### Kafka topic detail: ${topic}"
  topic_detail="$(docker compose exec -T "${KAFKA_SERVICE}" \
    kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" --describe --topic "${topic}" | tr -d '\r')"
  echo "${topic_detail}"

  if ! grep -q "PartitionCount: ${KAFKA_EXPECTED_PARTITIONS}" <<< "${topic_detail}"; then
    echo "Topic '${topic}' partition count does not match ${KAFKA_EXPECTED_PARTITIONS}." >&2
    exit 1
  fi

  if ! grep -q "ReplicationFactor: ${KAFKA_EXPECTED_REPLICATION_FACTOR}" <<< "${topic_detail}"; then
    echo "Topic '${topic}' replication factor does not match ${KAFKA_EXPECTED_REPLICATION_FACTOR}." >&2
    exit 1
  fi
done

echo
echo "Kafka topic smoke completed."
