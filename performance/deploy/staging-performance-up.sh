#!/usr/bin/env bash

set -euo pipefail

ENV_FILE="${ENV_FILE:-.env}"

if [[ -f "${ENV_FILE}" ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// }" ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    value="${value%\"}"
    value="${value#\"}"
    value="${value%\'}"
    value="${value#\'}"
    export "$key=$value"
  done < "${ENV_FILE}"
fi

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.performance.yml)
BASE_URL="${BASE_URL:-http://localhost:8081/api}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
HOST_ELASTICSEARCH_URL="${HOST_ELASTICSEARCH_URL:-http://localhost:9200}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"
EXPECTED_MIN_RESULT_COUNT="${EXPECTED_MIN_RESULT_COUNT:-1}"
HEALTH_WAIT_TIMEOUT_SECONDS="${HEALTH_WAIT_TIMEOUT_SECONDS:-240}"
HEALTH_WAIT_INTERVAL_SECONDS="${HEALTH_WAIT_INTERVAL_SECONDS:-5}"
REINDEX_LOG_TIMEOUT_SECONDS="${REINDEX_LOG_TIMEOUT_SECONDS:-240}"
REINDEX_LOG_TAIL_LINES="${REINDEX_LOG_TAIL_LINES:-2000}"
ELASTICSEARCH_REINDEX_ON_STARTUP="${ELASTICSEARCH_REINDEX_ON_STARTUP:-false}"
ELASTICSEARCH_REINDEX_BATCH_SIZE="${ELASTICSEARCH_REINDEX_BATCH_SIZE:-500}"
BUILD_SERVICES="${BUILD_SERVICES:-backend gateway elasticsearch}"
UP_SERVICES="${UP_SERVICES:-}"
PERFORMANCE_REINDEX_RESULT="ok"

export ELASTICSEARCH_REINDEX_ON_STARTUP
export ELASTICSEARCH_REINDEX_BATCH_SIZE

echo "ENV_FILE=${ENV_FILE}"
echo "BASE_URL=${BASE_URL}"
echo "BACKEND_URL=${BACKEND_URL}"
echo "GATEWAY_URL=${GATEWAY_URL}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "GRAFANA_URL=${GRAFANA_URL}"
echo "ZIPKIN_URL=${ZIPKIN_URL}"
echo "ELASTICSEARCH_URL=${ELASTICSEARCH_URL}"
echo "HOST_ELASTICSEARCH_URL=${HOST_ELASTICSEARCH_URL}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "EXPECTED_MIN_RESULT_COUNT=${EXPECTED_MIN_RESULT_COUNT}"
echo "HEALTH_WAIT_TIMEOUT_SECONDS=${HEALTH_WAIT_TIMEOUT_SECONDS}"
echo "REINDEX_LOG_TIMEOUT_SECONDS=${REINDEX_LOG_TIMEOUT_SECONDS}"
echo "REINDEX_LOG_TAIL_LINES=${REINDEX_LOG_TAIL_LINES}"
echo "ELASTICSEARCH_REINDEX_ON_STARTUP=${ELASTICSEARCH_REINDEX_ON_STARTUP}"
echo "ELASTICSEARCH_REINDEX_BATCH_SIZE=${ELASTICSEARCH_REINDEX_BATCH_SIZE}"
echo "BUILD_SERVICES=${BUILD_SERVICES}"
echo "UP_SERVICES=${UP_SERVICES:-all}"
echo

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

run_step() {
  local step_name="$1"
  shift

  echo "================================================================================"
  echo "### ${step_name}"
  echo "================================================================================"
  "$@"
  echo
}

require_env_file() {
  [[ -f "${ENV_FILE}" ]] || fail "${ENV_FILE} does not exist. Copy performance/deploy/staging.env.example to ${ENV_FILE} and fill server values."
  echo "env_file=ok"
}

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

compose_health() {
  local service_name="$1"

  compose ps --format json "${service_name}" \
    | jq -r 'if type == "array" then .[0].Health else .Health end // "unknown"'
}

compose_state() {
  local service_name="$1"

  compose ps --format json "${service_name}" \
    | jq -r 'if type == "array" then .[0].State else .State end // "unknown"'
}

print_service_diagnostics() {
  local service_name="$1"

  echo
  echo "### ${service_name} diagnostics"
  compose ps -a "${service_name}" || true

  local container_id
  container_id="$(compose ps -q "${service_name}" || true)"
  if [[ -n "${container_id}" ]]; then
    docker inspect "${container_id}" \
      --format 'container={{.Name}} state={{.State.Status}} exit={{.State.ExitCode}} health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' \
      || true
    docker inspect "${container_id}" \
      --format '{{range .State.Health.Log}}{{println .End .ExitCode .Output}}{{end}}' \
      || true
  fi

  case "${service_name}" in
    backend)
      curl -s "${BACKEND_URL}/actuator/health/liveness" || true
      echo
      curl -s "${BACKEND_URL}/actuator/health/readiness" || true
      echo
      curl -s "${BACKEND_URL}/actuator/health" || true
      echo
      ;;
    gateway)
      curl -s "${GATEWAY_URL}/actuator/health/liveness" || true
      echo
      curl -s "${GATEWAY_URL}/actuator/health/readiness" || true
      echo
      curl -s "${GATEWAY_URL}/actuator/health" || true
      echo
      ;;
    elasticsearch)
      curl -s "${HOST_ELASTICSEARCH_URL}/_cluster/health?pretty" || true
      echo
      curl -s "${HOST_ELASTICSEARCH_URL}/_cat/nodes?v&h=name,heap.percent,ram.percent,cpu" || true
      echo
      ;;
  esac

  compose logs --tail=240 "${service_name}" || true
}

wait_for_healthy() {
  local service_name="$1"
  local elapsed=0

  while (( elapsed <= HEALTH_WAIT_TIMEOUT_SECONDS )); do
    local health
    local state
    health="$(compose_health "${service_name}")"
    state="$(compose_state "${service_name}")"

    echo "${service_name}_state=${state} ${service_name}_health=${health} elapsed=${elapsed}s"

    if [[ "${health}" == "healthy" ]]; then
      return
    fi

    sleep "${HEALTH_WAIT_INTERVAL_SECONDS}"
    elapsed=$((elapsed + HEALTH_WAIT_INTERVAL_SECONDS))
  done

  compose ps
  print_service_diagnostics "${service_name}"
  fail "${service_name} did not become healthy within ${HEALTH_WAIT_TIMEOUT_SECONDS}s"
}

wait_for_reindex() {
  local elapsed=0

  if [[ "${ELASTICSEARCH_REINDEX_ON_STARTUP}" != "true" ]]; then
    PERFORMANCE_REINDEX_RESULT="skipped"
    echo "performance_reindex=skipped"
    echo "reason=ELASTICSEARCH_REINDEX_ON_STARTUP=${ELASTICSEARCH_REINDEX_ON_STARTUP}"
    return
  fi

  while (( elapsed <= REINDEX_LOG_TIMEOUT_SECONDS )); do
    if compose logs --tail="${REINDEX_LOG_TAIL_LINES}" backend | grep -q 'Job search reindex completed'; then
      compose logs --tail="${REINDEX_LOG_TAIL_LINES}" backend | grep -Ei 'reindex|indexedCount' || true
      return
    fi

    if compose logs --tail=120 backend | grep -q 'Application run failed'; then
      compose logs --tail=160 backend
      fail "backend application failed during startup"
    fi

    echo "reindex_wait_elapsed=${elapsed}s"
    sleep "${HEALTH_WAIT_INTERVAL_SECONDS}"
    elapsed=$((elapsed + HEALTH_WAIT_INTERVAL_SECONDS))
  done

  compose logs --tail="${REINDEX_LOG_TAIL_LINES}" backend | grep -Ei 'reindex|indexedCount|Application run failed|alias' || true
  fail "backend reindex completion log was not found within ${REINDEX_LOG_TIMEOUT_SECONDS}s"
}

run_step "Env file check" \
  require_env_file

run_step "Server bootstrap check" \
  bash performance/deploy/server-bootstrap-check.sh

run_step "Performance compose config" \
  compose config

run_step "Start performance database dependencies" \
  compose up -d mysql redis elasticsearch zookeeper kafka

run_step "Wait for mysql health" \
  wait_for_healthy mysql

run_step "Wait for kafka health" \
  wait_for_healthy kafka

run_step "Ensure Kafka topics" \
  bash performance/events/ensure-kafka-topics.sh

run_step "Kafka topic smoke" \
  bash performance/events/kafka-topic-smoke.sh

run_step "Performance database preparation" \
  bash performance/dataset/prepare-performance-database.sh

if [[ -n "${BUILD_SERVICES}" ]]; then
  # shellcheck disable=SC2086
  run_step "Build performance services" \
    compose build ${BUILD_SERVICES}
fi

if [[ -n "${UP_SERVICES}" ]]; then
  # shellcheck disable=SC2086
  run_step "Start performance stack" \
    compose up -d ${UP_SERVICES}
else
  run_step "Start performance stack" \
    compose up -d
fi

run_step "Compose service status" \
  compose ps

run_step "Wait for backend health" \
  wait_for_healthy backend

run_step "Wait for gateway health" \
  wait_for_healthy gateway

run_step "Security event pipeline smoke" \
  env \
    GATEWAY_URL="${GATEWAY_URL}" \
    ELASTICSEARCH_URL="${SECURITY_SMOKE_ELASTICSEARCH_URL:-${HOST_ELASTICSEARCH_URL}}" \
    bash performance/security/security-event-pipeline-smoke.sh

run_step "Wait for performance reindex" \
  wait_for_reindex

run_step "Outbox Kafka publish smoke" \
  bash performance/events/outbox-kafka-publish-smoke.sh

run_step "Kafka consumer smoke" \
  bash performance/events/kafka-consumer-smoke.sh

run_step "Performance profile smoke" \
  env \
    BASE_URL="${BASE_URL}" \
    EXPECTED_MIN_RESULT_COUNT="${EXPECTED_MIN_RESULT_COUNT}" \
    bash performance/dataset/performance-profile-smoke.sh

echo "### Staging Performance Up Summary"
echo "env_file=ok"
echo "server_bootstrap_check=ok"
echo "performance_database_preparation=ok"
echo "compose_config=ok"
echo "performance_stack=up"
echo "backend_health=healthy"
echo "gateway_health=healthy"
echo "performance_reindex=${PERFORMANCE_REINDEX_RESULT}"
echo "performance_profile_smoke=ok"
echo "kafka=healthy"
echo "kafka_topics=ok"
echo "outbox_kafka_publish=ok"
echo "kafka_consumers=ok"
echo "security_event_pipeline=ok"
echo
echo "Staging performance stack is ready for pre-k6 smoke."
