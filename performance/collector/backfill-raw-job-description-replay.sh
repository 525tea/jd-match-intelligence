#!/usr/bin/env bash

set -euo pipefail

SOURCES="${SOURCES:-JUMPIT,WANTED}"
PROFILE="${PROFILE:-local}"

echo "SOURCES=${SOURCES}"
echo "PROFILE=${PROFILE}"
echo "NOTE: This replays preserved jobs.raw_data to regenerate jobs.description."
echo "NOTE: Run backend Flyway migration first so the latest skill seed/alias data exists in MySQL."

cd "$(dirname "$0")/../../collector"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} --spring.main.web-application-type=none --app.backfill.raw-job-description-replay.enabled=true --app.backfill.raw-job-description-replay.sources=${SOURCES}"
