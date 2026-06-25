#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

KAFKA_SERVICE="${KAFKA_SERVICE:-kafka}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
KAFKA_EXPECTED_TOPICS="${KAFKA_EXPECTED_TOPICS:-job.created email.send es.index}"
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
  echo "Start Kafka first:" >&2
  echo "  docker compose up -d zookeeper kafka" >&2
  exit 1
fi

for topic in ${KAFKA_EXPECTED_TOPICS}; do
  echo "### Ensure Kafka topic: ${topic}"
  docker compose exec -T "${KAFKA_SERVICE}" \
    kafka-topics \
      --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
      --create \
      --if-not-exists \
      --topic "${topic}" \
      --partitions "${KAFKA_EXPECTED_PARTITIONS}" \
      --replication-factor "${KAFKA_EXPECTED_REPLICATION_FACTOR}"
done

echo
echo "Kafka topic ensure completed."
