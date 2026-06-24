#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/performance/dataset/performance.env"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-jobflow-mysql}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
PERF_MIN_JOB_COUNT="${PERF_MIN_JOB_COUNT:-1000}"

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  echo "Refusing to run performance dataset gate against real database: ${PERF_DB_NAME}" >&2
  exit 1
fi

echo "ROOT_DIR=${ROOT_DIR}"
echo "MYSQL_CONTAINER=${MYSQL_CONTAINER}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "PERF_MIN_JOB_COUNT=${PERF_MIN_JOB_COUNT}"

TMP_SQL="$(mktemp)"
sed \
  -e "s/{{PERF_DB_NAME}}/${PERF_DB_NAME}/g" \
  -e "s/{{PERF_MIN_JOB_COUNT}}/${PERF_MIN_JOB_COUNT}/g" \
  "${ROOT_DIR}/performance/dataset/performance-dataset-gate.sql" > "${TMP_SQL}"

docker compose exec -T mysql mysql \
  -u"${PERF_DB_USER}" \
  -p"${PERF_DB_PASSWORD}" \
  --default-character-set=utf8mb4 \
  "${PERF_DB_NAME}" < "${TMP_SQL}"

rm -f "${TMP_SQL}"

echo "Performance dataset gate completed."
