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
JOB_EVENTS_TOPIC="${JOB_EVENTS_TOPIC:-job.created}"
EMAIL_SEND_TOPIC="${EMAIL_SEND_TOPIC:-email.send}"
KAFKA_CONSUMER_SMOKE_RUN_ID="${KAFKA_CONSUMER_SMOKE_RUN_ID:-kafka-consumer-smoke-$(date +%Y%m%d%H%M%S)}"
KAFKA_CONSUMER_SMOKE_WAIT_SECONDS="${KAFKA_CONSUMER_SMOKE_WAIT_SECONDS:-45}"

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

wait_for_log() {
  local description="$1"
  local message_pattern="$2"
  local run_pattern="$3"

  for ((i = 1; i <= KAFKA_CONSUMER_SMOKE_WAIT_SECONDS; i++)); do
    local logs
    logs="$(compose logs --since=5m backend)"
    if printf '%s\n' "${logs}" | grep -F "${message_pattern}" | grep -Fq "${run_pattern}"; then
      echo "${description}=ok"
      return
    fi

    echo "consumer_wait_elapsed=${i}s ${description}=waiting"
    sleep 1
  done

  compose logs --since=5m backend | grep -Ei 'Kafka|consumer|error|exception' || true
  fail "${description} was not observed in backend logs"
}

echo "ROOT_DIR=${ROOT_DIR}"
echo "ENV_FILE=${ENV_FILE}"
echo "COMPOSE_FILES=${COMPOSE_FILES[*]}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "KAFKA_SERVICE=${KAFKA_SERVICE}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}"
echo "JOB_EVENTS_TOPIC=${JOB_EVENTS_TOPIC}"
echo "EMAIL_SEND_TOPIC=${EMAIL_SEND_TOPIC}"
echo "KAFKA_CONSUMER_SMOKE_RUN_ID=${KAFKA_CONSUMER_SMOKE_RUN_ID}"
echo "KAFKA_CONSUMER_SMOKE_WAIT_SECONDS=${KAFKA_CONSUMER_SMOKE_WAIT_SECONDS}"
echo

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  fail "Refusing to run Kafka consumer smoke against real database: ${PERF_DB_NAME}"
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

echo "### Select indexed job target"
job_id="$(
  mysql_exec -Nse "
    SELECT id
    FROM jobs
    WHERE status = 'OPEN'
    ORDER BY id DESC
    LIMIT 1;
  "
)"

if [[ -z "${job_id}" ]]; then
  fail "No OPEN job row found for Kafka consumer smoke"
fi

echo "kafka_consumer_smoke_job_id=${job_id}"
echo

echo "### Publish job search index consumer smoke event"
job_event_id=$((8000000000 + $(date +%s) % 1000000000))
job_message="$(
	  jq -cn \
	    --argjson eventId "${job_event_id}" \
	    --argjson jobId "${job_id}" \
	    --arg topic "${JOB_EVENTS_TOPIC}" \
	    --arg smokeRunId "${KAFKA_CONSUMER_SMOKE_RUN_ID}" \
	    '{
	      eventId: $eventId,
	      aggregateType: "JOB",
	      aggregateId: $jobId,
	      eventType: "JOB_UPDATED",
	      topic: $topic,
	      payload: {
	        jobId: $jobId,
	        smokeRunId: $smokeRunId
      }
    }'
)"
publish_message "${JOB_EVENTS_TOPIC}" "JOB:${job_id}" "${job_message}"

echo "### Publish email send consumer smoke event"
email_message="$(
  jq -cn \
    --arg smokeRunId "${KAFKA_CONSUMER_SMOKE_RUN_ID}" \
    '{
      to: "user@example.com",
      subject: ("Kafka consumer smoke " + $smokeRunId),
      text: "Kafka consumer smoke email body",
      html: null,
      smokeRunId: $smokeRunId
    }'
)"
publish_message "${EMAIL_SEND_TOPIC}" "EMAIL:${KAFKA_CONSUMER_SMOKE_RUN_ID}" "${email_message}"

echo
echo "### Wait for backend consumer handling"
wait_for_log \
  "job_search_index_consumer" \
  "Kafka job search index event handled" \
  "kafka_consumer_smoke_run_id=${KAFKA_CONSUMER_SMOKE_RUN_ID}"
wait_for_log \
  "email_send_consumer" \
  "Kafka email send event handled" \
  "kafka_consumer_smoke_run_id=${KAFKA_CONSUMER_SMOKE_RUN_ID}"

echo
echo "### Kafka Consumer Smoke Summary"
echo "job_search_index_consumer=ok"
echo "email_send_consumer=ok"
echo "kafka_consumer_smoke_run_id=${KAFKA_CONSUMER_SMOKE_RUN_ID}"
echo
echo "Kafka consumer smoke completed."
