#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"

echo "PROFILE=${PROFILE}"
echo "NOTE: This command rebuilds job_skill_index from current OPEN job_skills."
echo "NOTE: Run backend Flyway migration first so job_skill_index exists."

cd "$(dirname "$0")/../../backend"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} \
--spring.main.web-application-type=none \
--jobflow.analytics.job-skill-index.runner.enabled=true"
