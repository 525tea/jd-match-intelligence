#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
SECURITY_EVENTS_INDEX="${SECURITY_EVENTS_INDEX:-jobflow-security-events}"
LOGSTASH_SERVICE="${LOGSTASH_SERVICE:-logstash}"
KIBANA_SERVICE="${KIBANA_SERVICE:-kibana}"
SMOKE_WAIT_SECONDS="${SMOKE_WAIT_SECONDS:-60}"
LOGSTASH_READY_WAIT_SECONDS="${LOGSTASH_READY_WAIT_SECONDS:-60}"
SMOKE_REQUEST_PATH="${SMOKE_REQUEST_PATH:-/api/.env}"
SMOKE_REQUEST_ID="${SMOKE_REQUEST_ID:-security-event-smoke-$(date +%Y%m%d%H%M%S)}"

cd "${ROOT_DIR}"

echo "ROOT_DIR=${ROOT_DIR}"
echo "GATEWAY_URL=${GATEWAY_URL}"
echo "ELASTICSEARCH_URL=${ELASTICSEARCH_URL}"
echo "SECURITY_EVENTS_INDEX=${SECURITY_EVENTS_INDEX}"
echo "LOGSTASH_SERVICE=${LOGSTASH_SERVICE}"
echo "KIBANA_SERVICE=${KIBANA_SERVICE}"
echo "SMOKE_WAIT_SECONDS=${SMOKE_WAIT_SECONDS}"
echo "LOGSTASH_READY_WAIT_SECONDS=${LOGSTASH_READY_WAIT_SECONDS}"
echo "SMOKE_REQUEST_PATH=${SMOKE_REQUEST_PATH}"
echo "SMOKE_REQUEST_ID=${SMOKE_REQUEST_ID}"
echo

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

require_running_service() {
  local service_name="$1"

  if ! docker compose ps --services --filter status=running | grep -qx "${service_name}"; then
    fail "service \"${service_name}\" is not running"
  fi
}

require_running_service "${LOGSTASH_SERVICE}"
require_running_service "${KIBANA_SERVICE}"

echo "### Wait for Logstash security pipeline"
for ((i = 1; i <= LOGSTASH_READY_WAIT_SECONDS; i++)); do
  if docker compose logs --tail=300 "${LOGSTASH_SERVICE}" \
    | grep -Eq "Pipeline.*started|Successfully started Logstash API endpoint"; then
    echo "logstash_pipeline=ready"
    break
  fi

  if (( i == LOGSTASH_READY_WAIT_SECONDS )); then
    docker compose logs --tail=160 "${LOGSTASH_SERVICE}" || true
    fail "Logstash pipeline was not ready within ${LOGSTASH_READY_WAIT_SECONDS}s"
  fi

  echo "logstash_ready_wait_elapsed=${i}s"
  sleep 1
done

echo "### Trigger abnormal gateway request"
http_status="$(
  curl -s -o /tmp/jobflow-security-event-smoke-response.txt -w "%{http_code}" \
    -H "X-Request-Id: ${SMOKE_REQUEST_ID}" \
    -H "User-Agent: jobflow-security-event-smoke" \
    "${GATEWAY_URL}${SMOKE_REQUEST_PATH}" || true
)"
echo "smoke_request_status=${http_status}"

case "${http_status}" in
  400|401|403|404|405|429|500|502|503)
    ;;
  *)
    cat /tmp/jobflow-security-event-smoke-response.txt || true
    fail "unexpected smoke request status ${http_status}"
    ;;
esac

query_payload="$(mktemp)"
cat > "${query_payload}" <<JSON
{
  "query": {
    "bool": {
      "filter": [
        { "term": { "requestId.keyword": "${SMOKE_REQUEST_ID}" } },
        { "term": { "eventType.keyword": "ABNORMAL_REQUEST" } },
        { "term": { "path.keyword": "${SMOKE_REQUEST_PATH}" } }
      ]
    }
  },
  "size": 1,
  "sort": [
    { "@timestamp": { "order": "desc" } }
  ]
}
JSON

echo
echo "### Wait for security event in Elasticsearch"
for ((i = 1; i <= SMOKE_WAIT_SECONDS; i++)); do
  result_count="$(
    curl -s -H "Content-Type: application/json" \
      -X POST "${ELASTICSEARCH_URL}/${SECURITY_EVENTS_INDEX}/_search" \
      --data-binary "@${query_payload}" \
      | jq -r '.hits.total.value // 0'
  )"

  echo "security_event_wait_elapsed=${i}s result_count=${result_count}"

  if [[ "${result_count}" != "0" ]]; then
    echo
    echo "### Security event sample"
    curl -s -H "Content-Type: application/json" \
      -X POST "${ELASTICSEARCH_URL}/${SECURITY_EVENTS_INDEX}/_search" \
      --data-binary "@${query_payload}" \
      | jq '.hits.hits[0]._source'

    rm -f "${query_payload}"
    echo
    echo "Security event pipeline smoke completed."
    exit 0
  fi

  sleep 1
done

rm -f "${query_payload}"
fail "security event was not indexed within ${SMOKE_WAIT_SECONDS}s"
