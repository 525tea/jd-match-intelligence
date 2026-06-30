#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ALLOW_REINDEX_ON_STARTUP="${ALLOW_REINDEX_ON_STARTUP:-false}"

fail() {
  echo "Assertion failed: $*" >&2
  exit 1
}

assert_file_exists() {
  local file_path="$1"
  [[ -f "${file_path}" ]] || fail "missing required file: ${file_path}"
}

assert_contains() {
  local file_path="$1"
  local expected="$2"
  local message="$3"

  if ! grep -Fq "${expected}" "${file_path}"; then
    fail "${message}. Expected to find '${expected}' in ${file_path}"
  fi
}

assert_not_contains_regex() {
  local file_path="$1"
  local pattern="$2"
  local message="$3"

  if grep -Eq "${pattern}" "${file_path}"; then
    fail "${message}. Pattern '${pattern}' found in ${file_path}"
  fi
}

assert_compose_json() {
  local expression="$1"
  local message="$2"

  if ! jq -e "${expression}" >/dev/null <<< "${performance_compose_json}"; then
    fail "${message}. Expression: ${expression}"
  fi
}

require_command() {
  local command_name="$1"

  if ! command -v "${command_name}" >/dev/null 2>&1; then
    fail "required command '${command_name}' is not installed"
  fi
}

cd "${ROOT_DIR}"

RUNBOOK="performance/deploy/STAGING_DEPLOY_RUNBOOK.md"
STAGING_UP="performance/deploy/staging-performance-up.sh"
SERVER_BOOTSTRAP="performance/deploy/server-bootstrap-check.sh"
HOST_TUNING_SNAPSHOT="performance/deploy/performance-host-tuning-snapshot.sh"
HOST_TUNING_APPLY="performance/deploy/performance-host-tuning-apply.sh"
STAGING_ENV_EXAMPLE="performance/deploy/staging.env.example"
COMPOSE_PERF="docker-compose.performance.yml"

KAFKA_SMOKE_SCRIPTS=(
  performance/events/ensure-kafka-topics.sh
  performance/events/kafka-topic-smoke.sh
  performance/events/outbox-kafka-publish-smoke.sh
  performance/events/kafka-consumer-smoke.sh
)

CACHE_STRESS_RUNNERS=(
  performance/k6/run-stress-es-cache.sh
  performance/k6/run-stress-es-cache-mixed-hit-rate.sh
  performance/k6/run-stress-es-cache-saturation.sh
)

ANALYSIS_CACHE_STRESS_RUNNERS=(
  performance/k6/run-stress-analysis-cache.sh
)

echo "ROOT_DIR=${ROOT_DIR}"
echo

require_command docker
require_command jq

echo "### Required operational guard files"
assert_file_exists "${RUNBOOK}"
assert_file_exists "${STAGING_UP}"
assert_file_exists "${SERVER_BOOTSTRAP}"
assert_file_exists "${HOST_TUNING_SNAPSHOT}"
assert_file_exists "${HOST_TUNING_APPLY}"
assert_file_exists "${STAGING_ENV_EXAMPLE}"
assert_file_exists "${COMPOSE_PERF}"

for script in "${KAFKA_SMOKE_SCRIPTS[@]}" "${CACHE_STRESS_RUNNERS[@]}" "${ANALYSIS_CACHE_STRESS_RUNNERS[@]}"; do
  assert_file_exists "${script}"
done

echo "required_operational_guard_files=ok"
echo

echo "### Performance compose invariants"
performance_compose_json="$(docker compose -f docker-compose.yml -f docker-compose.performance.yml config --format json)"

assert_compose_json '.services.backend.healthcheck.test | tostring | contains("/actuator/health/liveness")' \
  "performance backend healthcheck must use liveness, not full /actuator/health"
assert_contains "${COMPOSE_PERF}" 'CACHE_ENABLED: "${PERF_CACHE_ENABLED:-false}"' \
  "performance backend cache must be controlled by PERF_CACHE_ENABLED"
assert_contains "${COMPOSE_PERF}" 'ELASTICSEARCH_REINDEX_ON_STARTUP: ${ELASTICSEARCH_REINDEX_ON_STARTUP:-false}' \
  "performance reindex flag must default to false in compose"
assert_contains "${COMPOSE_PERF}" 'MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED: ${PERF_MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED:-false}' \
  "performance backend must not let Elasticsearch health decide container lifecycle by default"
assert_compose_json '.services.backend.environment.MANAGEMENT_HEALTH_ELASTICSEARCH_ENABLED == "false"' \
  "rendered performance backend must not let Elasticsearch health decide container lifecycle"
assert_compose_json '.services.backend.environment.SPRING_KAFKA_BOOTSTRAP_SERVERS == "kafka:29092"' \
  "performance backend Kafka bootstrap must default to kafka:29092"
assert_compose_json '.services.backend.environment.APP_NOTIFICATION_EMAIL_PROVIDER == "mock"' \
  "performance notification provider must default to mock"
assert_compose_json '.services.backend.environment.SERVER_TOMCAT_THREADS_MAX != null' \
  "performance backend Tomcat max threads must be externally tunable"
assert_compose_json '.services.backend.environment.SERVER_TOMCAT_MAX_CONNECTIONS != null' \
  "performance backend Tomcat max connections must be externally tunable"
assert_compose_json '.services.backend.environment.HIKARI_MAXIMUM_POOL_SIZE != null' \
  "performance backend Hikari maximum pool size must be externally tunable"
assert_compose_json '.services.backend.environment.TRACING_SAMPLING_PROBABILITY == "0.0"' \
  "performance backend tracing sampling must default to 0.0 for saturation tests"
assert_compose_json '.services.kafka.ports[] | select(.target == 9092 and .published == "19092")' \
  "performance Kafka host-mapped port must default to 19092"

rendered_reindex="$(jq -r '.services.backend.environment.ELASTICSEARCH_REINDEX_ON_STARTUP // ""' <<< "${performance_compose_json}")"
if [[ "${rendered_reindex}" == "true" && "${ALLOW_REINDEX_ON_STARTUP}" != "true" ]]; then
  fail "rendered ELASTICSEARCH_REINDEX_ON_STARTUP=true. Keep it false for repeated stress tests, or rerun with ALLOW_REINDEX_ON_STARTUP=true only for intentional index rebuild."
fi

echo "performance_compose_invariants=ok"
echo

echo "### Kafka smoke invariants"
for script in "${KAFKA_SMOKE_SCRIPTS[@]}"; do
  assert_contains "${script}" 'COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.performance.yml)' \
    "${script} must use both compose files"
  assert_contains "${script}" 'KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:29092}"' \
    "${script} must default Kafka bootstrap to kafka:29092 inside the Docker network"
  assert_contains "${script}" 'cd "${ROOT_DIR}"' \
    "${script} must cd to ROOT_DIR before invoking docker compose"
  assert_not_contains_regex "${script}" 'localhost:9092|(^|[[:space:]])source[[:space:]].*ENV_FILE|(^|[[:space:]])\.[[:space:]].*ENV_FILE' \
    "${script} must not regress to localhost:9092 or source/dot-load .env"
done

assert_contains "${STAGING_UP}" "validate_kafka_smoke_context" \
  "staging-performance-up.sh must run Kafka smoke context guard"
assert_contains "${STAGING_UP}" "recover_kafka_zookeeper_node_exists" \
  "staging-performance-up.sh must include Kafka/ZooKeeper NodeExists recovery"
assert_contains "${STAGING_UP}" "compose_volume_name" \
  "staging-performance-up.sh must derive Kafka/ZooKeeper volume names from Compose"

echo "kafka_smoke_invariants=ok"
echo

echo "### Host tuning invariants"
assert_contains "${HOST_TUNING_APPLY}" 'APPLY="${APPLY:-false}"' \
  "host tuning apply script must default to dry-run"
assert_contains "${HOST_TUNING_APPLY}" "Rerun with APPLY=true" \
  "host tuning apply script must explain explicit apply mode"
assert_contains "${HOST_TUNING_SNAPSHOT}" "net.core.somaxconn" \
  "host tuning snapshot must capture network backlog sysctl values"
assert_contains "${HOST_TUNING_SNAPSHOT}" "SERVER_TOMCAT" \
  "host tuning snapshot must capture backend Tomcat tuning env"

echo "host_tuning_invariants=ok"
echo

echo "### k6 search stress invariants"
for runner in "${CACHE_STRESS_RUNNERS[@]}" "${ANALYSIS_CACHE_STRESS_RUNNERS[@]}" performance/k6/run-stress-es-nocache.sh; do
  assert_file_exists "${runner}"
  assert_contains "${runner}" 'BASE_URL="${BASE_URL:-http://localhost:8080}"' \
    "${runner} must default to backend direct, not Gateway"
  assert_contains "${runner}" '/actuator/health/liveness' \
    "${runner} must use liveness health preflight"
done

for runner in "${CACHE_STRESS_RUNNERS[@]}"; do
  assert_contains "${runner}" "MIN_CACHE_HIT_DELTA" \
    "${runner} must verify cache hit delta before stress test"
  assert_contains "${runner}" "cache_gets_total" \
    "${runner} must verify cache metrics before stress test"
done

for runner in "${ANALYSIS_CACHE_STRESS_RUNNERS[@]}"; do
  assert_contains "${runner}" "MIN_ANALYSIS_CACHE_HIT_DELTA" \
    "${runner} must verify analysis cache hit delta before cache-enabled stress test"
  assert_contains "${runner}" "cache_gets_total" \
    "${runner} must verify cache metrics before stress test"
  assert_contains "${runner}" "gapAnalysis" \
    "${runner} must include gapAnalysis cache metrics"
  assert_contains "${runner}" "jdMatch" \
    "${runner} must include jdMatch cache metrics"
  assert_contains "${runner}" "jobRecommendation" \
    "${runner} must include jobRecommendation cache metrics"
done

echo "k6_search_stress_invariants=ok"
echo

echo "### Env template invariants"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_SPRING_PROFILES_ACTIVE=local,performance" \
  "staging env example must keep performance profile explicit"
assert_contains "${STAGING_ENV_EXAMPLE}" "ELASTICSEARCH_REINDEX_ON_STARTUP=false" \
  "staging env example must default reindex to false"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_CACHE_ENABLED" \
  "staging env example must expose performance cache flag"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_KAFKA_BOOTSTRAP_SERVERS=kafka:29092" \
  "staging env example must document container-internal Kafka bootstrap"
assert_contains "${STAGING_ENV_EXAMPLE}" "KAFKA_BOOTSTRAP_SERVERS=localhost:19092" \
  "staging env example must document host-side Kafka bootstrap"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_NOTIFICATION_EMAIL_PROVIDER" \
  "staging env example must expose mock notification provider for performance runs"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_SERVER_TOMCAT_THREADS_MAX" \
  "staging env example must expose performance Tomcat max threads tuning"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_SERVER_TOMCAT_MAX_CONNECTIONS" \
  "staging env example must expose performance Tomcat max connections tuning"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_HIKARI_MAXIMUM_POOL_SIZE" \
  "staging env example must expose performance Hikari maximum pool tuning"
assert_contains "${STAGING_ENV_EXAMPLE}" "PERF_TRACING_SAMPLING_PROBABILITY=0.0" \
  "staging env example must disable tracing sampling for saturation tests by default"

echo "staging_env_invariants=ok"
echo

echo "### Runbook invariants"
assert_contains "${RUNBOOK}" "performance-ops-regression-guard.sh" \
  "runbook must document the operational regression guard"
assert_contains "${RUNBOOK}" "NodeExistsException" \
  "runbook must document Kafka/ZooKeeper NodeExists recovery"
assert_contains "${RUNBOOK}" "kafka:29092" \
  "runbook must document container-internal Kafka bootstrap"
assert_contains "${RUNBOOK}" "localhost:19092" \
  "runbook must document host-side Kafka bootstrap"
assert_contains "${RUNBOOK}" "docker compose down -v" \
  "runbook must warn against volume-destructive compose down"
assert_contains "${RUNBOOK}" "/actuator/health/liveness" \
  "runbook must document liveness health checks"

echo "runbook_invariants=ok"
echo

echo "### Performance Ops Regression Guard Summary"
echo "required_operational_guard_files=ok"
echo "performance_compose_invariants=ok"
echo "kafka_smoke_invariants=ok"
echo "host_tuning_invariants=ok"
echo "k6_search_stress_invariants=ok"
echo "staging_env_invariants=ok"
echo "runbook_invariants=ok"
echo
echo "Performance ops regression guard completed."
