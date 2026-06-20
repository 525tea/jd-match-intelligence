#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"
SOURCES="${SOURCES:-JUMPIT,WANTED}"

echo "PROFILE=${PROFILE}"
echo "SOURCES=${SOURCES}"

./gradlew :collector:bootRun \
  --args="--spring.profiles.active=${PROFILE} --spring.main.web-application-type=none --app.normalization-candidate-collection.enabled=true --app.normalization-candidate-collection.sources=${SOURCES}"
