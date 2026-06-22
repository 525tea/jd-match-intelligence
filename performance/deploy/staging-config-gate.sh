#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

BACKEND_APP_YML="${ROOT_DIR}/backend/src/main/resources/application.yml"
COLLECTOR_APP_PROPERTIES="${ROOT_DIR}/collector/src/main/resources/application.properties"
COLLECTOR_LOCAL_PROPERTIES="${ROOT_DIR}/collector/src/main/resources/application-local.properties"
GATEWAY_APP_YML="${ROOT_DIR}/gateway/src/main/resources/application.yml"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
BATCH_MIGRATION="${ROOT_DIR}/backend/src/main/resources/db/migration/V17__add_spring_batch_metadata_tables.sql"
ENV_TEMPLATE="${ROOT_DIR}/performance/deploy/staging.env.example"
RUNBOOK="${ROOT_DIR}/performance/deploy/STAGING_DEPLOY_RUNBOOK.md"
PRE_K6_SMOKE="${ROOT_DIR}/performance/deploy/staging-pre-k6-smoke.sh"
ACTUATOR_SMOKE="${ROOT_DIR}/performance/security/actuator-exposure-smoke.sh"
ACTUATOR_REPORT="${ROOT_DIR}/docs/metrics/security/260622_actuator_exposure_smoke_report.md"

echo "ROOT_DIR=${ROOT_DIR}"
echo

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "Missing required command: ${command_name}" >&2
    exit 1
  fi
}

assert_file_exists() {
  local file_path="$1"
  local message="$2"

  if [[ ! -f "${file_path}" ]]; then
    echo "Assertion failed: ${message}" >&2
    echo "Missing file: ${file_path}" >&2
    exit 1
  fi
}

assert_contains() {
  local file_path="$1"
  local expected="$2"
  local message="$3"

  if ! grep -Fq "${expected}" "${file_path}"; then
    echo "Assertion failed: ${message}" >&2
    echo "Expected to find: ${expected}" >&2
    echo "File: ${file_path}" >&2
    exit 1
  fi
}

assert_compose_json() {
  local expression="$1"
  local message="$2"

  if ! jq -e "${expression}" >/dev/null <<< "${compose_json}"; then
    echo "Assertion failed: ${message}" >&2
    echo "Expression: ${expression}" >&2
    exit 1
  fi
}

require_command docker
require_command jq

echo "### Required files"
assert_file_exists "${BACKEND_APP_YML}" "backend application.yml should exist"
assert_file_exists "${COLLECTOR_APP_PROPERTIES}" "collector application.properties should exist"
assert_file_exists "${COLLECTOR_LOCAL_PROPERTIES}" "collector application-local.properties should exist"
assert_file_exists "${GATEWAY_APP_YML}" "gateway application.yml should exist"
assert_file_exists "${COMPOSE_FILE}" "docker-compose.yml should exist"
assert_file_exists "${BATCH_MIGRATION}" "Spring Batch metadata migration should exist"
assert_file_exists "${ENV_TEMPLATE}" "staging env template should exist"
assert_file_exists "${RUNBOOK}" "staging deploy runbook should exist"
assert_file_exists "${PRE_K6_SMOKE}" "staging pre-k6 smoke should exist"
assert_file_exists "${ACTUATOR_SMOKE}" "actuator exposure smoke should exist"
assert_file_exists "${ACTUATOR_REPORT}" "actuator exposure smoke report should exist"
echo "required_files=ok"
echo

echo "### Backend runtime settings"
assert_contains "${BACKEND_APP_YML}" "shutdown: graceful" \
  "backend should enable graceful shutdown"
assert_contains "${BACKEND_APP_YML}" "timeout-per-shutdown-phase: \${SHUTDOWN_PHASE_TIMEOUT:30s}" \
  "backend should configure graceful shutdown timeout"
assert_contains "${BACKEND_APP_YML}" "open-in-view: false" \
  "backend should disable JPA open-in-view"
assert_contains "${BACKEND_APP_YML}" "maximum-pool-size: \${HIKARI_MAXIMUM_POOL_SIZE:20}" \
  "backend should externalize Hikari maximum pool size"
assert_contains "${BACKEND_APP_YML}" "minimum-idle: \${HIKARI_MINIMUM_IDLE:5}" \
  "backend should externalize Hikari minimum idle"
assert_contains "${BACKEND_APP_YML}" "initialize-schema: never" \
  "backend should not rely on Spring Batch automatic schema initialization"
echo "backend_runtime_settings=ok"
echo

echo "### Collector runtime settings"
assert_contains "${COLLECTOR_APP_PROPERTIES}" "server.shutdown=graceful" \
  "collector should enable graceful shutdown"
assert_contains "${COLLECTOR_APP_PROPERTIES}" "spring.lifecycle.timeout-per-shutdown-phase=\${SHUTDOWN_PHASE_TIMEOUT:30s}" \
  "collector should configure graceful shutdown timeout"
assert_contains "${COLLECTOR_APP_PROPERTIES}" "management.endpoints.web.exposure.include=health,info,prometheus" \
  "collector should expose prometheus endpoint"
assert_contains "${COLLECTOR_LOCAL_PROPERTIES}" "spring.datasource.hikari.maximum-pool-size=\${HIKARI_MAXIMUM_POOL_SIZE:10}" \
  "collector should externalize Hikari maximum pool size"
echo "collector_runtime_settings=ok"
echo

echo "### Gateway runtime settings"
assert_contains "${GATEWAY_APP_YML}" "shutdown: graceful" \
  "gateway should enable graceful shutdown"
assert_contains "${GATEWAY_APP_YML}" "timeout-per-shutdown-phase: \${SHUTDOWN_PHASE_TIMEOUT:30s}" \
  "gateway should configure graceful shutdown timeout"
assert_contains "${GATEWAY_APP_YML}" "include: health,info,prometheus" \
  "gateway should expose prometheus endpoint"
echo "gateway_runtime_settings=ok"
echo

echo "### Batch migration"
assert_contains "${BATCH_MIGRATION}" "BATCH_JOB_INSTANCE" \
  "Spring Batch metadata migration should create BATCH_JOB_INSTANCE"
assert_contains "${BATCH_MIGRATION}" "BATCH_STEP_EXECUTION" \
  "Spring Batch metadata migration should create BATCH_STEP_EXECUTION"
echo "spring_batch_metadata_migration=ok"
echo

echo "### Env template"
assert_contains "${ENV_TEMPLATE}" "JWT_SECRET=change-me-at-least-32-bytes" \
  "env template should document JWT secret"
assert_contains "${ENV_TEMPLATE}" "GITHUB_CLIENT_SECRET=change-me" \
  "env template should include GitHub client secret placeholder"
assert_contains "${ENV_TEMPLATE}" "OAUTH2_SUCCESS_REDIRECT_URI=http://localhost:5173/oauth2/success" \
  "env template should include OAuth success redirect"
assert_contains "${ENV_TEMPLATE}" "DEMO_LOGIN_ENABLED=false" \
  "staging env template should disable demo login by default"
assert_contains "${ENV_TEMPLATE}" "SKILL_TREND_AGGREGATION_SCHEDULER_ENABLED=false" \
  "staging env template should disable skill trend scheduler by default"
assert_contains "${ENV_TEMPLATE}" "DEADLINE_REMINDER_SCHEDULER_ENABLED=false" \
  "staging env template should disable deadline reminder scheduler by default"
echo "staging_env_template=ok"
echo

echo "### Actuator exposure artifacts"
assert_contains "${RUNBOOK}" "actuator-exposure-smoke.sh" \
  "runbook should include actuator exposure smoke"
assert_contains "${ACTUATOR_SMOKE}" '${BASE_URL}/actuator/health' \
  "actuator exposure smoke should verify proxied backend health boundary"
assert_contains "${ACTUATOR_SMOKE}" '${BASE_URL}/actuator/prometheus' \
  "actuator exposure smoke should verify proxied backend prometheus boundary"
assert_contains "${ACTUATOR_REPORT}" "Gateway-proxied" \
  "actuator exposure report should document gateway-proxied actuator boundary"
echo "actuator_exposure_artifacts=ok"
echo

echo "### Pre-k6 smoke artifacts"
assert_contains "${PRE_K6_SMOKE}" "staging-readiness-smoke.sh" \
  "pre-k6 smoke should run staging readiness smoke"
assert_contains "${PRE_K6_SMOKE}" "job-list-filter-smoke.sh" \
  "pre-k6 smoke should run job list filter smoke"
assert_contains "${PRE_K6_SMOKE}" "search-intent-smoke.sh" \
  "pre-k6 smoke should run search intent smoke"
assert_contains "${PRE_K6_SMOKE}" "actuator-exposure-smoke.sh" \
  "pre-k6 smoke should run actuator exposure smoke"
assert_contains "${RUNBOOK}" "staging-pre-k6-smoke.sh" \
  "runbook should include staging pre-k6 smoke"
echo "pre_k6_smoke_artifacts=ok"
echo

echo "### Docker Compose config"
compose_json="$(
  cd "${ROOT_DIR}" \
    && docker compose config --format json
)"

assert_compose_json '.services.mysql.mem_limit != null' \
  "mysql should have memory limit"
assert_compose_json '.services.redis.mem_limit != null' \
  "redis should have memory limit"
assert_compose_json '.services.elasticsearch.mem_limit != null' \
  "elasticsearch should have memory limit"
assert_compose_json '.services.backend.mem_limit != null' \
  "backend should have memory limit"
assert_compose_json '.services.gateway.mem_limit != null' \
  "gateway should have memory limit"
assert_compose_json '.services.prometheus.mem_limit != null' \
  "prometheus should have memory limit"
assert_compose_json '.services.grafana.mem_limit != null' \
  "grafana should have memory limit"
assert_compose_json '.services.zipkin.mem_limit != null' \
  "zipkin should have memory limit"

assert_compose_json '.services.backend.healthcheck.test != null' \
  "backend should have healthcheck"
assert_compose_json '.services.gateway.healthcheck.test != null' \
  "gateway should have healthcheck"

assert_compose_json '.services.backend.depends_on.mysql.condition == "service_healthy"' \
  "backend should wait for healthy mysql"
assert_compose_json '.services.backend.depends_on.redis.condition == "service_healthy"' \
  "backend should wait for healthy redis"
assert_compose_json '.services.backend.depends_on.elasticsearch.condition == "service_healthy"' \
  "backend should wait for healthy elasticsearch"
assert_compose_json '.services.gateway.depends_on.backend.condition == "service_healthy"' \
  "gateway should wait for healthy backend"
assert_compose_json '.services.gateway.depends_on.redis.condition == "service_healthy"' \
  "gateway should wait for healthy redis"
assert_compose_json '.services.prometheus.depends_on.backend.condition == "service_healthy"' \
  "prometheus should wait for healthy backend"
assert_compose_json '.services.prometheus.depends_on.gateway.condition == "service_healthy"' \
  "prometheus should wait for healthy gateway"

assert_compose_json '.services.backend.environment.JAVA_TOOL_OPTIONS != null' \
  "backend should configure container-aware JVM options"
assert_compose_json '.services.gateway.environment.JAVA_TOOL_OPTIONS != null' \
  "gateway should configure container-aware JVM options"
assert_compose_json '.services.elasticsearch.environment.ES_JAVA_OPTS != null' \
  "elasticsearch should configure JVM heap"
echo "docker_compose_config=ok"
echo

echo "### Staging Config Gate Summary"
echo "required_files=ok"
echo "backend_runtime_settings=ok"
echo "collector_runtime_settings=ok"
echo "gateway_runtime_settings=ok"
echo "spring_batch_metadata_migration=ok"
echo "staging_env_template=ok"
echo "actuator_exposure_artifacts=ok"
echo "pre_k6_smoke_artifacts=ok"
echo "docker_compose_config=ok"

echo
echo "Staging config gate completed."
