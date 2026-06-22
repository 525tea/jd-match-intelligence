#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8081}"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
GRAFANA_URL="${GRAFANA_URL:-http://localhost:3001}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
ELASTICSEARCH_URL="${ELASTICSEARCH_URL:-http://localhost:9200}"

echo "BASE_URL=${BASE_URL}"
echo "BACKEND_URL=${BACKEND_URL}"
echo "GATEWAY_URL=${GATEWAY_URL}"
echo "PROMETHEUS_URL=${PROMETHEUS_URL}"
echo "GRAFANA_URL=${GRAFANA_URL}"
echo "ZIPKIN_URL=${ZIPKIN_URL}"
echo "ELASTICSEARCH_URL=${ELASTICSEARCH_URL}"
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

run_step "Search intent smoke" \
  env \
    BASE_URL="${BASE_URL}" \
    bash performance/elasticsearch/search-intent-smoke.sh

run_step "Actuator exposure smoke" \
  env \
    BACKEND_URL="${BACKEND_URL}" \
    GATEWAY_URL="${GATEWAY_URL}" \
    BASE_URL="${BASE_URL}" \
    bash performance/security/actuator-exposure-smoke.sh

echo "### Staging Pre-k6 Smoke Summary"
echo "staging_config_gate=ok"
echo "staging_readiness_smoke=ok"
echo "job_list_filter_smoke=ok"
echo "search_intent_smoke=ok"
echo "actuator_exposure_smoke=ok"

echo
echo "Staging pre-k6 smoke completed."
