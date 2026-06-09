#!/usr/bin/env bash

set -euo pipefail

SOURCE="${SOURCE:-WANTED}"
COLLECT_LIMIT="${COLLECT_LIMIT:-50}"
PREVIEW_LIMIT="${PREVIEW_LIMIT:-5}"
SCAN_LIMIT="${SCAN_LIMIT:-100}"
PROFILE="${PROFILE:-local}"

echo "SOURCE=${SOURCE}"
echo "COLLECT_LIMIT=${COLLECT_LIMIT}"
echo "PREVIEW_LIMIT=${PREVIEW_LIMIT}"
echo "SCAN_LIMIT=${SCAN_LIMIT}"
echo "PROFILE=${PROFILE}"

cd "$(dirname "$0")/../../collector"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} --spring.main.web-application-type=none --app.collector.enabled=true --app.collector.source=${SOURCE} --app.collector.collect-limit=${COLLECT_LIMIT} --app.collector.preview-limit=${PREVIEW_LIMIT} --app.collector.scan-limit=${SCAN_LIMIT}"
