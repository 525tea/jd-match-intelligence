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
KAFKA_CONSUMER_GROUP_ID="${KAFKA_CONSUMER_GROUP_ID:-jobflow-backend-performance}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
SNAPSHOT_LABEL="${SNAPSHOT_LABEL:-kafka-consumer-lag-$(date +%Y%m%d%H%M%S)}"
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

if ! compose ps --services --filter status=running | grep -qx "${KAFKA_SERVICE}"; then
  fail "service \"${KAFKA_SERVICE}\" is not running"
fi

lag_text_file="${ARTIFACT_DIR}/${SNAPSHOT_LABEL}_consumer_group_lag.txt"
prometheus_lag_file="${ARTIFACT_DIR}/${SNAPSHOT_LABEL}_prometheus_kafka_lag.json"
prometheus_offset_file="${ARTIFACT_DIR}/${SNAPSHOT_LABEL}_prometheus_kafka_current_offset.json"

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "KAFKA_CONSUMER_GROUP_ID=${KAFKA_CONSUMER_GROUP_ID}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "SNAPSHOT_LABEL=${SNAPSHOT_LABEL}"
echo

echo "### Kafka consumer group lag"
set +e
compose exec -T "${KAFKA_SERVICE}" \
  kafka-consumer-groups \
    --bootstrap-server "${KAFKA_BOOTSTRAP_SERVERS}" \
    --describe \
    --group "${KAFKA_CONSUMER_GROUP_ID}" \
  > "${lag_text_file}" 2>&1
consumer_group_status=$?
set -e

cat "${lag_text_file}"

if [[ "${consumer_group_status}" -ne 0 ]]; then
  echo "consumer_group_lag_status=failed"
  echo "consumer_group_lag_file=${lag_text_file}"
  exit "${consumer_group_status}"
fi

total_lag="$(
  awk '
    NR == 1 { next }
    $0 ~ /^[[:space:]]*$/ { next }
    $6 ~ /^[0-9]+$/ { sum += $6 }
    END { print sum + 0 }
  ' "${lag_text_file}"
)"

echo
echo "### Prometheus Kafka lag query"
if curl -fsS \
  --get "${PROMETHEUS_URL}/api/v1/query" \
  --data-urlencode 'query=sum(clamp_min(kafka_consumergroup_lag{consumergroup=~"jobflow-.*"}, 0)) by (consumergroup, topic)' \
  -o "${prometheus_lag_file}"; then
  jq '.' "${prometheus_lag_file}" || cat "${prometheus_lag_file}"
else
  echo '{"status":"unavailable","reason":"prometheus kafka lag query failed"}' > "${prometheus_lag_file}"
  cat "${prometheus_lag_file}"
fi

echo
echo "### Prometheus Kafka current offset query"
if curl -fsS \
  --get "${PROMETHEUS_URL}/api/v1/query" \
  --data-urlencode 'query=sum(kafka_consumergroup_current_offset{consumergroup=~"jobflow-.*"}) by (consumergroup, topic)' \
  -o "${prometheus_offset_file}"; then
  jq '.' "${prometheus_offset_file}" || cat "${prometheus_offset_file}"
else
  echo '{"status":"unavailable","reason":"prometheus kafka current offset query failed"}' > "${prometheus_offset_file}"
  cat "${prometheus_offset_file}"
fi

echo
echo "### Kafka Consumer Lag Snapshot Summary"
echo "consumer_group=${KAFKA_CONSUMER_GROUP_ID}"
echo "total_lag=${total_lag}"
echo "consumer_group_lag_file=${lag_text_file}"
echo "prometheus_lag_file=${prometheus_lag_file}"
echo "prometheus_current_offset_file=${prometheus_offset_file}"
