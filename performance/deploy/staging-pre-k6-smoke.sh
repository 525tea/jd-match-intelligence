#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"
EXPECTED_MIN_RESULT_COUNT="${EXPECTED_MIN_RESULT_COUNT:-1}"
RUN_SEARCH_INTENT_SMOKE="${RUN_SEARCH_INTENT_SMOKE:-false}"

echo "BASE_URL=${BASE_URL}"
echo "BACKEND_URL=${BACKEND_URL}"
echo "GATEWAY_URL=${GATEWAY_URL}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "GRAFANA_URL=${GRAFANA_URL}"
echo "ZIPKIN_URL=${ZIPKIN_URL}"
echo "ELASTICSEARCH_URL=${ELASTICSEARCH_URL}"
echo "EXPECTED_MIN_RESULT_COUNT=${EXPECTED_MIN_RESULT_COUNT}"
echo "RUN_SEARCH_INTENT_SMOKE=${RUN_SEARCH_INTENT_SMOKE}"
echo

run_step() {
  local step_name="$1"
  shift

  echo "================================================================================"
  echo "### ${step_name}"
  echo "================================================================================"
  "$@"
  echo
}

run_step "Staging config gate" \
  bash performance/deploy/staging-config-gate.sh

run_step "Staging readiness smoke" \
  env \
    BASE_URL="${BASE_URL}" \
    BACKEND_URL="${BACKEND_URL}" \
    GATEWAY_URL="${GATEWAY_URL}" \
    PROMETHEUS_URL="${PROMETHEUS_URL}" \
    GRAFANA_URL="${GRAFANA_URL}" \
    ZIPKIN_URL="${ZIPKIN_URL}" \
    ELASTICSEARCH_URL="${ELASTICSEARCH_URL}" \
    bash performance/deploy/staging-readiness-smoke.sh

run_step "Job list filter smoke" \
  env \
    BASE_URL="${BASE_URL}" \
    bash performance/job/job-list-filter-smoke.sh

if [[ "${RUN_SEARCH_INTENT_SMOKE}" == "true" ]]; then
  run_step "Search intent smoke" \
    env \
      BASE_URL="${BASE_URL}" \
      bash performance/elasticsearch/search-intent-smoke.sh
else
  echo "================================================================================"
  echo "### Search intent smoke"
  echo "================================================================================"
  echo "search_intent_smoke=skipped"
  echo "reason=performance fixture readiness uses performance-profile-smoke instead of real-data ranking assumptions"
  echo
fi

run_step "Actuator exposure smoke" \
  env \
    BACKEND_URL="${BACKEND_URL}" \
    GATEWAY_URL="${GATEWAY_URL}" \
    BASE_URL="${BASE_URL}" \
    bash performance/security/actuator-exposure-smoke.sh

run_step "Performance profile smoke" \
  env \
    BASE_URL="${BASE_URL}" \
    EXPECTED_MIN_RESULT_COUNT="${EXPECTED_MIN_RESULT_COUNT}" \
    bash performance/dataset/performance-profile-smoke.sh

echo "### Staging Pre-k6 Smoke Summary"
echo "staging_config_gate=ok"
echo "staging_readiness_smoke=ok"
echo "job_list_filter_smoke=ok"
if [[ "${RUN_SEARCH_INTENT_SMOKE}" == "true" ]]; then
  echo "search_intent_smoke=ok"
else
  echo "search_intent_smoke=skipped"
fi
echo "actuator_exposure_smoke=ok"
echo "performance_profile_smoke=ok"

echo
echo "Staging pre-k6 smoke completed."
