#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${ROOT_DIR}/performance/dataset/performance.env"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

MYSQL_CONTAINER="${MYSQL_CONTAINER:-jobflow-mysql}"
MYSQL_ROOT_USER="${MYSQL_ROOT_USER:-root}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root}"
PERF_DB_NAME="${PERF_DB_NAME:-jobflow_perf}"
PERF_DB_USER="${PERF_DB_USER:-jobflow}"
PERF_DB_PASSWORD="${PERF_DB_PASSWORD:-jobflow}"
PERF_JOB_COUNT="${PERF_JOB_COUNT:-1000}"
RESET_PERF_DB="${RESET_PERF_DB:-false}"

if [[ "${PERF_DB_NAME}" == "jobflow" ]]; then
  echo "Refusing to seed performance data into the real application database: ${PERF_DB_NAME}" >&2
  exit 1
fi

echo "ROOT_DIR=${ROOT_DIR}"
echo "MYSQL_CONTAINER=${MYSQL_CONTAINER}"
echo "PERF_DB_NAME=${PERF_DB_NAME}"
echo "PERF_JOB_COUNT=${PERF_JOB_COUNT}"
echo "RESET_PERF_DB=${RESET_PERF_DB}"

if [[ "${RESET_PERF_DB}" == "true" ]]; then
  echo "Resetting performance database: ${PERF_DB_NAME}"
  docker compose exec -T mysql mysql \
    -u"${MYSQL_ROOT_USER}" \
    -p"${MYSQL_ROOT_PASSWORD}" \
    -e "DROP DATABASE IF EXISTS \`${PERF_DB_NAME}\`;"
fi

docker compose exec -T mysql mysql \
  -u"${MYSQL_ROOT_USER}" \
  -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS \`${PERF_DB_NAME}\`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON \`${PERF_DB_NAME}\`.* TO '${PERF_DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL

TABLE_COUNT="$(
  docker compose exec -T mysql mysql \
    -N -B \
    -u"${MYSQL_ROOT_USER}" \
    -p"${MYSQL_ROOT_PASSWORD}" \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${PERF_DB_NAME}';"
)"

if [[ "${TABLE_COUNT}" == "0" ]]; then
  echo "Applying backend migrations to ${PERF_DB_NAME}"
  while IFS= read -r migration; do
    echo "Applying $(basename "${migration}")"
    docker compose exec -T mysql mysql \
      -u"${PERF_DB_USER}" \
      -p"${PERF_DB_PASSWORD}" \
      --default-character-set=utf8mb4 \
      "${PERF_DB_NAME}" < "${migration}"
  done < <(find "${ROOT_DIR}/backend/src/main/resources/db/migration" -maxdepth 1 -name 'V*.sql' | sort -V)
else
  echo "Skipping migrations because ${PERF_DB_NAME} already has ${TABLE_COUNT} tables."
fi

TMP_SQL="$(mktemp)"
sed \
  -e "s/{{PERF_JOB_COUNT}}/${PERF_JOB_COUNT}/g" \
  "${ROOT_DIR}/performance/dataset/seed-performance-jobs.sql" > "${TMP_SQL}"

echo "Seeding synthetic performance jobs"
docker compose exec -T mysql mysql \
  -u"${PERF_DB_USER}" \
  -p"${PERF_DB_PASSWORD}" \
  --default-character-set=utf8mb4 \
  "${PERF_DB_NAME}" < "${TMP_SQL}"

rm -f "${TMP_SQL}"

echo "Performance database preparation completed."
