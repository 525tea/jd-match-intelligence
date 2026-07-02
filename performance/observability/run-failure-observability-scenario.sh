#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"

if [[ -f "${ENV_FILE}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ "${line}" =~ ^[[:space:]]*# ]] && continue
    [[ -z "${line// }" ]] && continue
    key="${line%%=*}"
    value="${line#*=}"
    value="${value%\"}" value="${value#\"}"
    value="${value%\'}" value="${value#\'}"
    export "${key}=${value}"
  done < "${ENV_FILE}"
fi

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.performance.yml)
SCENARIO_MODE="${SCENARIO_MODE:-all}"
RUN_ID="${RUN_ID:-failure-observability-$(date +%Y%m%d%H%M%S)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-${ROOT_DIR}/artifacts/observability/$(date +%y%m%d)_failure_observability}"

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
DEBEZIUM_CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://localhost:18083}"

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
REDIS_SERVICE="${REDIS_SERVICE:-redis}"
ELASTICSEARCH_SERVICE="${ELASTICSEARCH_SERVICE:-elasticsearch}"
BACKEND_SERVICE="${BACKEND_SERVICE:-backend}"
GATEWAY_SERVICE="${GATEWAY_SERVICE:-gateway}"
LOGSTASH_SERVICE="${LOGSTASH_SERVICE:-logstash}"
KIBANA_SERVICE="${KIBANA_SERVICE:-kibana}"
DEBEZIUM_CONNECT_SERVICE="${DEBEZIUM_CONNECT_SERVICE:-debezium-connect}"

SECURITY_EVENTS_INDEX="${SECURITY_EVENTS_INDEX:-jobflow-security-events}"
OBSERVABILITY_WAIT_SECONDS="${OBSERVABILITY_WAIT_SECONDS:-90}"
SECURITY_EVENT_WAIT_SECONDS="${SECURITY_EVENT_WAIT_SECONDS:-90}"
KAFKA_RECOVERY_EVENT_COUNT="${KAFKA_RECOVERY_EVENT_COUNT:-10000}"
DEBEZIUM_RECOVERY_EVENT_COUNT="${DEBEZIUM_RECOVERY_EVENT_COUNT:-10000}"
FAILURE_SEARCH_KEYWORD="${FAILURE_SEARCH_KEYWORD:-backend}"
FAILURE_SEARCH_LIMIT="${FAILURE_SEARCH_LIMIT:-5}"
REQUIRE_ES_FALLBACK_200="${REQUIRE_ES_FALLBACK_200:-true}"
REQUIRE_REDIS_DEGRADATION_200="${REQUIRE_REDIS_DEGRADATION_200:-true}"

cd "${ROOT_DIR}"
mkdir -p "${ARTIFACT_DIR}"

STOPPED_SERVICES=()

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    fail "Missing required command: ${command_name}"
  fi
}

require_service_running() {
  local service="$1"
  if ! compose ps --services --filter status=running | grep -qx "${service}"; then
    fail "service \"${service}\" is not running"
  fi
}

wait_http_ok() {
  local label="$1"
  local url="$2"
  for ((i = 1; i <= OBSERVABILITY_WAIT_SECONDS; i++)); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      echo "${label}=UP"
      return
    fi
    sleep 1
  done
  fail "${label} did not become available: ${url}"
}

restore_stopped_services() {
  if [[ "${#STOPPED_SERVICES[@]}" -eq 0 ]]; then
    return
  fi

  echo
  echo "### Restore stopped services"
  for service in "${STOPPED_SERVICES[@]}"; do
    echo "restore_service=${service}"
    compose up -d "${service}" >/dev/null
  done

  for service in "${STOPPED_SERVICES[@]}"; do
    case "${service}" in
      "${ELASTICSEARCH_SERVICE}")
        wait_http_ok "elasticsearch_after_restore" "${ELASTICSEARCH_URL}/_cluster/health"
        ;;
      "${REDIS_SERVICE}")
        for ((i = 1; i <= OBSERVABILITY_WAIT_SECONDS; i++)); do
          if compose exec -T "${REDIS_SERVICE}" redis-cli ping 2>/dev/null | grep -qx "PONG"; then
            echo "redis_after_restore=UP"
            break
          fi
          [[ "${i}" -eq "${OBSERVABILITY_WAIT_SECONDS}" ]] && fail "redis did not recover"
          sleep 1
        done
        ;;
    esac
  done
}

trap restore_stopped_services EXIT

timestamp() {
  date +%Y%m%d%H%M%S
}

http_status() {
  local url="$1"
  local body_file="$2"
  shift 2
  curl -sS -o "${body_file}" -w "%{http_code}" "$@" "${url}" || true
}

prometheus_query() {
  local label="$1"
  local query="$2"
  local output_file="${ARTIFACT_DIR}/$(timestamp)_${RUN_ID}_${label}.json"
  curl -sS --get --data-urlencode "query=${query}" \
    "${PROMETHEUS_URL}/api/v1/query" \
    | tee "${output_file}" >/dev/null
  echo "${output_file}"
}

print_capture_marker() {
  local name="$1"
  local reason="$2"
  echo
  echo "CAPTURE_NOW name=${name}"
  echo "CAPTURE_REASON=${reason}"
  echo "GRAFANA_BACKEND_URL=http://3.39.242.44:3001/d/jobflow-backend/jobflow-backend-observability?orgId=1&refresh=5s&from=now-15m&to=now"
  echo "KIBANA_URL=http://3.39.242.44:5601"
  echo
}

write_summary_line() {
  local key="$1"
  local value="$2"
  echo "${key}=${value}" | tee -a "${SUMMARY_FILE}"
}

stop_service_for_fault() {
  local service="$1"
  require_service_running "${service}"
  echo "stop_service=${service}"
  compose stop "${service}" >/dev/null
  STOPPED_SERVICES+=("${service}")
}

run_security_event_scenario() {
  local scenario_prefix="${RUN_ID}-security"
  local query_file="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_query.json"
  local result_file="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_search_result.json"

  echo
  echo "### Scenario: security events -> Logstash -> Elasticsearch -> Kibana"
  require_service_running "${LOGSTASH_SERVICE}"
  require_service_running "${KIBANA_SERVICE}"

  declare -a request_specs=(
    "ABNORMAL_REQUEST|/api/.env|jobflow-observability-abnormal"
    "AUTH_FAILURE|/api/auth/me|jobflow-observability-auth"
    "ABNORMAL_REQUEST|/api/unknown-observability-${RUN_ID}|jobflow-observability-unknown"
  )

  for spec in "${request_specs[@]}"; do
    IFS='|' read -r expected_type path suffix <<< "${spec}"
    request_id="${scenario_prefix}-${suffix}"
    body_file="${ARTIFACT_DIR}/$(timestamp)_${request_id}_response.txt"
    status="$(http_status "${GATEWAY_URL}${path}" "${body_file}" \
      -H "X-Request-Id: ${request_id}" \
      -H "User-Agent: jobflow-failure-observability")"
    echo "security_request path=${path} expected_type=${expected_type} status=${status} request_id=${request_id}"
  done

  cat > "${query_file}" <<JSON
{
  "query": {
    "bool": {
      "filter": [
        { "prefix": { "requestId.keyword": "${scenario_prefix}" } }
      ]
    }
  },
  "aggs": {
    "by_event_type": {
      "terms": {
        "field": "eventType.keyword",
        "size": 10
      }
    },
    "by_status": {
      "terms": {
        "field": "status",
        "size": 10
      }
    }
  },
  "size": 10,
  "sort": [
    { "@timestamp": { "order": "desc" } }
  ]
}
JSON

  local total_hits="0"
  for ((i = 1; i <= SECURITY_EVENT_WAIT_SECONDS; i++)); do
    curl -sS -H "Content-Type: application/json" \
      -X POST "${ELASTICSEARCH_URL}/${SECURITY_EVENTS_INDEX}/_search" \
      --data-binary "@${query_file}" \
      | tee "${result_file}" >/dev/null
    total_hits="$(jq -r '.hits.total.value // 0' "${result_file}")"
    echo "security_event_wait_elapsed=${i}s total_hits=${total_hits}"
    [[ "${total_hits}" -ge 3 ]] && break
    sleep 1
  done

  [[ "${total_hits}" -ge 3 ]] || fail "expected at least 3 security events, got ${total_hits}"

  jq '.aggregations.by_event_type.buckets' "${result_file}" \
    | tee "${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_event_type_buckets.json" >/dev/null
  print_capture_marker \
    "security_events_kibana_${RUN_ID}" \
    "Kibana에서 requestId prefix ${scenario_prefix}, eventType별 ABNORMAL_REQUEST/AUTH_FAILURE 집계가 보이는 시점"
  write_summary_line "security_events_total_hits" "${total_hits}"
}

run_elasticsearch_fallback_scenario() {
  local scenario_prefix="${RUN_ID}-elasticsearch"
  local search_url="${GATEWAY_URL}/api/jobs/search?keyword=${FAILURE_SEARCH_KEYWORD}&limit=${FAILURE_SEARCH_LIMIT}"

  echo
  echo "### Scenario: Elasticsearch down -> search fallback/degradation -> recovery"
  require_service_running "${ELASTICSEARCH_SERVICE}"
  wait_http_ok "gateway_before_es_fault" "${GATEWAY_URL}/actuator/health"

  before_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_before_response.json"
  before_status="$(http_status "${search_url}" "${before_body}")"
  echo "es_before_status=${before_status}"

  stop_service_for_fault "${ELASTICSEARCH_SERVICE}"
  sleep 8

  during_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_during_response.json"
  during_status="$(http_status "${search_url}" "${during_body}")"
  echo "es_during_status=${during_status}"

  prometheus_query "es_down_http_status_count" \
    'sum by (uri, status) (increase(http_server_requests_seconds_count{application="jobflow-backend"}[5m]))' >/dev/null
  prometheus_query "es_down_latency_p95" \
    'histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{application="jobflow-backend"}[5m])))' >/dev/null
  prometheus_query "es_down_search_fallback_total" \
    'sum(increase(jobflow_search_fallback_total{source="elasticsearch",target="mysql_fulltext"}[5m]))' >/dev/null

  print_capture_marker \
    "elasticsearch_down_search_${RUN_ID}" \
    "Elasticsearch 중단 후 /jobs/search 요청을 보낸 직후. Grafana에서 latency/error/search fallback counter 양상을 확인"

  if [[ "${REQUIRE_ES_FALLBACK_200}" == "true" && "${during_status}" != "200" ]]; then
    fail "expected /jobs/search to return 200 while Elasticsearch is down, got ${during_status}"
  fi

  restore_stopped_services
  STOPPED_SERVICES=()
  wait_http_ok "gateway_after_es_restore" "${GATEWAY_URL}/actuator/health"

  after_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_after_response.json"
  after_status="$(http_status "${search_url}" "${after_body}")"
  echo "es_after_status=${after_status}"
  [[ "${after_status}" == "200" ]] || fail "expected /jobs/search to recover to 200 after Elasticsearch restore, got ${after_status}"

  write_summary_line "elasticsearch_before_status" "${before_status}"
  write_summary_line "elasticsearch_during_status" "${during_status}"
  write_summary_line "elasticsearch_after_status" "${after_status}"
}

run_redis_degradation_scenario() {
  local scenario_prefix="${RUN_ID}-redis"
  local search_url="${GATEWAY_URL}/api/jobs/search?keyword=${FAILURE_SEARCH_KEYWORD}&limit=${FAILURE_SEARCH_LIMIT}"
  local trends_url="${GATEWAY_URL}/api/trends/skills?limit=5"

  echo
  echo "### Scenario: Redis down -> cache/rate-limit degradation -> recovery"
  require_service_running "${REDIS_SERVICE}"
  wait_http_ok "gateway_before_redis_fault" "${GATEWAY_URL}/actuator/health"

  before_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_before_response.json"
  before_status="$(http_status "${search_url}" "${before_body}")"
  echo "redis_before_status=${before_status}"

  stop_service_for_fault "${REDIS_SERVICE}"
  sleep 8

  search_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_search_during_response.json"
  search_status="$(http_status "${search_url}" "${search_body}")"
  trends_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_trends_during_response.json"
  trends_status="$(http_status "${trends_url}" "${trends_body}")"
  echo "redis_during_search_status=${search_status}"
  echo "redis_during_trends_status=${trends_status}"

  prometheus_query "redis_down_http_status_count" \
    'sum by (uri, status) (increase(http_server_requests_seconds_count{application="jobflow-backend"}[5m]))' >/dev/null
  prometheus_query "redis_down_cache_gets" \
    'sum by (cache, result) (increase(cache_gets_total{application="jobflow-backend"}[5m]))' >/dev/null
  prometheus_query "redis_down_outbox_status" \
    'jobflow_outbox_events' >/dev/null

  print_capture_marker \
    "redis_down_degradation_${RUN_ID}" \
    "Redis 중단 후 search/trends 요청을 보낸 직후. Grafana에서 cache miss/error/latency 양상을 확인"

  if [[ "${REQUIRE_REDIS_DEGRADATION_200}" == "true" ]]; then
    [[ "${search_status}" == "200" ]] || fail "expected /jobs/search to return 200 while Redis is down, got ${search_status}"
    [[ "${trends_status}" == "200" ]] || fail "expected /trends/skills to return 200 while Redis is down, got ${trends_status}"
  fi

  restore_stopped_services
  STOPPED_SERVICES=()
  wait_http_ok "gateway_after_redis_restore" "${GATEWAY_URL}/actuator/health"

  after_body="${ARTIFACT_DIR}/$(timestamp)_${scenario_prefix}_after_response.json"
  after_status="$(http_status "${search_url}" "${after_body}")"
  echo "redis_after_status=${after_status}"
  [[ "${after_status}" == "200" ]] || fail "expected /jobs/search to recover to 200 after Redis restore, got ${after_status}"

  write_summary_line "redis_before_status" "${before_status}"
  write_summary_line "redis_during_search_status" "${search_status}"
  write_summary_line "redis_during_trends_status" "${trends_status}"
  write_summary_line "redis_after_status" "${after_status}"
}

run_kafka_consumer_recovery_scenario() {
  echo
  echo "### Scenario: Kafka consumer down -> lag accumulation -> recovery"
  KAFKA_RECOVERY_EVENT_COUNT="${KAFKA_RECOVERY_EVENT_COUNT}" \
  KAFKA_RECOVERY_RUN_ID="${RUN_ID}-kafka-consumer-recovery" \
  ARTIFACT_DIR="${ARTIFACT_DIR}" \
  bash performance/events/run-kafka-consumer-recovery-scenario.sh \
    | tee "${ARTIFACT_DIR}/$(timestamp)_${RUN_ID}_kafka_consumer_recovery.log"

  prometheus_query "kafka_recovery_outbox_status" \
    'jobflow_outbox_events' >/dev/null
  prometheus_query "kafka_recovery_consumer_lag" \
    'sum(clamp_min(kafka_consumergroup_lag{consumergroup=~"jobflow-.*"}, 0)) by (consumergroup, topic)' >/dev/null

  print_capture_marker \
    "kafka_consumer_recovery_${RUN_ID}" \
    "consumer disabled lag 누적, consumer 재기동 drain, final lag 0이 Grafana Kafka Consumer Lag 패널에 보이는 시점"
  write_summary_line "kafka_consumer_recovery" "completed"
}

run_debezium_recovery_scenario() {
  echo
  echo "### Scenario: Debezium pause/backend restart -> lag accumulation -> recovery"
  DEBEZIUM_RECOVERY_MODE="${DEBEZIUM_RECOVERY_MODE:-all}" \
  DEBEZIUM_RECOVERY_EVENT_COUNT="${DEBEZIUM_RECOVERY_EVENT_COUNT}" \
  DEBEZIUM_RECOVERY_RUN_ID="${RUN_ID}-debezium-recovery" \
  ARTIFACT_DIR="${ARTIFACT_DIR}" \
  bash performance/debezium/run-debezium-recovery-scenario.sh \
    | tee "${ARTIFACT_DIR}/$(timestamp)_${RUN_ID}_debezium_recovery.log"

  prometheus_query "debezium_recovery_outbox_status" \
    'jobflow_outbox_events' >/dev/null
  prometheus_query "debezium_recovery_consumer_lag" \
    'sum(clamp_min(kafka_consumergroup_lag{consumergroup=~"jobflow-.*"}, 0)) by (consumergroup, topic)' >/dev/null

  print_capture_marker \
    "debezium_recovery_${RUN_ID}" \
    "connector pause/resume 또는 backend consumer restart 후 processed count와 final lag 0이 확인된 시점"
  write_summary_line "debezium_recovery" "completed"
}

run_all_scenarios() {
  run_security_event_scenario
  run_elasticsearch_fallback_scenario
  run_redis_degradation_scenario
  run_kafka_consumer_recovery_scenario
  run_debezium_recovery_scenario
}

require_command curl
require_command jq

SUMMARY_FILE="${ARTIFACT_DIR}/$(timestamp)_${RUN_ID}_summary.txt"

{
  echo "ROOT_DIR=${ROOT_DIR}"
  echo "ENV_FILE=${ENV_FILE}"
  echo "SCENARIO_MODE=${SCENARIO_MODE}"
  echo "RUN_ID=${RUN_ID}"
  echo "ARTIFACT_DIR=${ARTIFACT_DIR}"
  echo "BACKEND_URL=${BACKEND_URL}"
  echo "GATEWAY_URL=${GATEWAY_URL}"
  echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
  echo "ELASTICSEARCH_URL=${ELASTICSEARCH_URL}"
  echo "DEBEZIUM_CONNECT_URL=${DEBEZIUM_CONNECT_URL}"
  echo
} | tee "${SUMMARY_FILE}"

case "${SCENARIO_MODE}" in
  all)
    run_all_scenarios
    ;;
  security-events)
    run_security_event_scenario
    ;;
  elasticsearch-fallback)
    run_elasticsearch_fallback_scenario
    ;;
  redis-degradation)
    run_redis_degradation_scenario
    ;;
  kafka-consumer-recovery)
    run_kafka_consumer_recovery_scenario
    ;;
  debezium-recovery)
    run_debezium_recovery_scenario
    ;;
  *)
    fail "Unsupported SCENARIO_MODE=${SCENARIO_MODE}. Use all, security-events, elasticsearch-fallback, redis-degradation, kafka-consumer-recovery, or debezium-recovery."
    ;;
esac

echo
echo "Failure observability scenario completed."
echo "mode=${SCENARIO_MODE}"
echo "run_id=${RUN_ID}"
echo "summary_file=${SUMMARY_FILE}"
echo "artifact_dir=${ARTIFACT_DIR}"
