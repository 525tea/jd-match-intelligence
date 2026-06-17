#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"
MODE="${MODE:-daily}"
DAILY_DIGEST_IDEMPOTENCY_TTL="${DAILY_DIGEST_IDEMPOTENCY_TTL:-25h}"
DAILY_DIGEST_MAX_ATTEMPTS="${DAILY_DIGEST_MAX_ATTEMPTS:-3}"
TARGET_USER_EMAIL_PATTERN="${TARGET_USER_EMAIL_PATTERN:-daily-digest-smoke-user-%@example.com}"
TARGET_ROLES="${TARGET_ROLES:-BACKEND,FULLSTACK}"
TARGET_CAREER_LEVEL="${TARGET_CAREER_LEVEL:-MID}"
MOCK_EMAIL_FAIL="${MOCK_EMAIL_FAIL:-false}"
MOCK_EMAIL_FAILURE_REASON="${MOCK_EMAIL_FAILURE_REASON:-Mock daily digest email send failed}"
MOCK_EMAIL_MESSAGE_ID_PREFIX="${MOCK_EMAIL_MESSAGE_ID_PREFIX:-mock-daily-digest}"

if [[ "${MODE}" != "daily" && "${MODE}" != "retry" ]]; then
  echo "Unsupported MODE=${MODE}. Use daily or retry." >&2
  exit 1
fi

echo "PROFILE=${PROFILE}"
echo "MODE=${MODE}"
echo "EMAIL_PROVIDER=mock"
echo "DAILY_DIGEST_IDEMPOTENCY_TTL=${DAILY_DIGEST_IDEMPOTENCY_TTL}"
echo "DAILY_DIGEST_MAX_ATTEMPTS=${DAILY_DIGEST_MAX_ATTEMPTS}"
echo "TARGET_USER_EMAIL_PATTERN=${TARGET_USER_EMAIL_PATTERN}"
echo "TARGET_ROLES=${TARGET_ROLES}"
echo "TARGET_CAREER_LEVEL=${TARGET_CAREER_LEVEL}"
echo "MOCK_EMAIL_FAIL=${MOCK_EMAIL_FAIL}"
echo "MOCK_EMAIL_MESSAGE_ID_PREFIX=${MOCK_EMAIL_MESSAGE_ID_PREFIX}"
echo "NOTE: This command runs the daily digest ApplicationRunner in non-web mode."
echo "NOTE: No real email is sent."

cd "$(dirname "$0")/../../backend"

start_epoch_ms="$(date +%s%3N)"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} \
--spring.main.web-application-type=none \
--jobflow.notification.daily-digest.runner.enabled=true \
--jobflow.notification.daily-digest.runner.mode=${MODE} \
--jobflow.notification.daily-digest.runner.target-roles=${TARGET_ROLES} \
--jobflow.notification.daily-digest.runner.target-career-level=${TARGET_CAREER_LEVEL} \
--jobflow.notification.deadline-reminder.runner.enabled=false \
--jobflow.notification.deadline-reminder.scheduler.enabled=false \
--jobflow.analytics.skill-trend.scheduler.enabled=false \
--jobflow.analytics.skill-trend.runner.enabled=false \
--jobflow.analytics.job-skill-index.runner.enabled=false \
--app.notification.email-provider=mock \
--app.notification.mock-email.fail=${MOCK_EMAIL_FAIL} \
--app.notification.mock-email.failure-reason=${MOCK_EMAIL_FAILURE_REASON} \
--app.notification.mock-email.provider-message-id-prefix=${MOCK_EMAIL_MESSAGE_ID_PREFIX} \
--app.notification.daily-digest.idempotency-ttl=${DAILY_DIGEST_IDEMPOTENCY_TTL} \
--app.notification.daily-digest.max-attempts=${DAILY_DIGEST_MAX_ATTEMPTS} \
--app.notification.daily-digest.target-user-email-pattern=${TARGET_USER_EMAIL_PATTERN}"

end_epoch_ms="$(date +%s%3N)"
duration_ms=$((end_epoch_ms - start_epoch_ms))

echo
echo "Daily digest mock smoke completed."
echo "duration_ms=${duration_ms}"
