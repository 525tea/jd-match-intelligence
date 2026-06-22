#!/usr/bin/env bash

set -euo pipefail

SOURCES="${SOURCES:-JUMPIT,WANTED}"
PROFILE="${PROFILE:-local}"
RAW_SNAPSHOT_STORAGE_ROOT="${RAW_SNAPSHOT_STORAGE_ROOT:-../build/raw-snapshots}"
PURGE_RAW_DATA_AFTER_SNAPSHOT="${PURGE_RAW_DATA_AFTER_SNAPSHOT:-false}"
MYSQL_DATABASE="${MYSQL_DATABASE:-jobflow}"
MYSQL_USER="${MYSQL_USER:-jobflow}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-jobflow}"

echo "SOURCES=${SOURCES}"
echo "PROFILE=${PROFILE}"
echo "RAW_SNAPSHOT_STORAGE_ROOT=${RAW_SNAPSHOT_STORAGE_ROOT}"
echo "PURGE_RAW_DATA_AFTER_SNAPSHOT=${PURGE_RAW_DATA_AFTER_SNAPSHOT}"
echo "NOTE: This backfills existing jobs.raw_data into raw snapshot storage."
echo "NOTE: It fills jobs.raw_snapshot_key/hash/size/storage metadata for rows that still keep raw_data in MySQL."
echo "NOTE: Set PURGE_RAW_DATA_AFTER_SNAPSHOT=true only after snapshot metadata has been verified."

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${REPO_ROOT}"

if command -v docker >/dev/null 2>&1 && docker compose ps mysql >/dev/null 2>&1; then
  raw_snapshot_column_count="$(
    docker compose exec -T mysql mysql \
      -u "${MYSQL_USER}" \
      -p"${MYSQL_PASSWORD}" \
      --batch \
      --skip-column-names \
      "${MYSQL_DATABASE}" \
      -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = '${MYSQL_DATABASE}' AND table_name = 'jobs' AND column_name IN ('raw_snapshot_key', 'raw_snapshot_hash', 'raw_snapshot_size_bytes', 'raw_snapshot_storage_type', 'raw_snapshot_saved_at');" \
      2>/dev/null || true
  )"

  if [[ "${raw_snapshot_column_count}" != "5" ]]; then
    echo "ERROR: jobs raw snapshot columns are missing in local MySQL."
    echo "ERROR: Run backend Flyway migration first, then retry this smoke."
    echo
    echo "Expected migration: backend/src/main/resources/db/migration/V19__add_raw_snapshot_metadata.sql"
    echo
    echo "Run one of these:"
    echo "  docker compose up -d --build backend gateway"
    echo "  ./gradlew -p backend bootRun --args=\"--spring.profiles.active=local --spring.main.web-application-type=none\""
    exit 1
  fi
fi

cd "${REPO_ROOT}/collector"

RAW_SNAPSHOT_STORAGE_ROOT="${RAW_SNAPSHOT_STORAGE_ROOT}" \
./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} --spring.main.web-application-type=none --app.backfill.raw-job-snapshot.enabled=true --app.backfill.raw-job-snapshot.sources=${SOURCES} --app.backfill.raw-job-snapshot.purge-raw-data-after-snapshot=${PURGE_RAW_DATA_AFTER_SNAPSHOT}"
