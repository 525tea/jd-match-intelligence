#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/performance/dataset/performance.env"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

MYSQL_SERVICE="${MYSQL_SERVICE:-mysql}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
SQL_FILE="${ROOT_DIR}/performance/events/event-processing-baseline-check.sql"

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  echo "Refusing to run event-processing baseline against real database: ${PERF_DB_NAME}" >&2
  exit 1
fi

echo "ROOT_DIR=${ROOT_DIR}"
echo "MYSQL_SERVICE=${MYSQL_SERVICE}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"

if ! docker compose ps --services --filter status=running | grep -qx "${MYSQL_SERVICE}"; then
  echo "service \"${MYSQL_SERVICE}\" is not running" >&2
  echo "Start the staging performance stack first:" >&2
  echo "  REQUIRED_PORTS=\"\" bash performance/deploy/staging-performance-up.sh" >&2
  exit 1
fi

docker compose exec -T "${MYSQL_SERVICE}" mysql \
  -u"${PERF_DB_USER}" \
  -p"${PERF_DB_PASSWORD}" \
  --default-character-set=utf8mb4 \
  "${PERF_DB_NAME}" < "${SQL_FILE}"

echo "Event processing baseline check completed."
