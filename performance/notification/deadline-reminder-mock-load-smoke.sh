#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"
MODE="${MODE:-due-soon}"
DEADLINE_REMINDER_WINDOW="${DEADLINE_REMINDER_WINDOW:-24h}"
DEADLINE_REMINDER_IDEMPOTENCY_TTL="${DEADLINE_REMINDER_IDEMPOTENCY_TTL:-25h}"
DEADLINE_REMINDER_MAX_ATTEMPTS="${DEADLINE_REMINDER_MAX_ATTEMPTS:-3}"
MOCK_EMAIL_FAIL="${MOCK_EMAIL_FAIL:-false}"
MOCK_EMAIL_FAILURE_REASON="${MOCK_EMAIL_FAILURE_REASON:-Mock email send failed}"
MOCK_EMAIL_MESSAGE_ID_PREFIX="${MOCK_EMAIL_MESSAGE_ID_PREFIX:-mock-deadline-reminder}"

if [[ "${MODE}" != "due-soon" && "${MODE}" != "retry" ]]; then
  echo "Unsupported MODE=${MODE}. Use due-soon or retry." >&2
  exit 1
fi

echo "PROFILE=${PROFILE}"
echo "MODE=${MODE}"
echo "EMAIL_PROVIDER=mock"
echo "DEADLINE_REMINDER_WINDOW=${DEADLINE_REMINDER_WINDOW}"
echo "DEADLINE_REMINDER_IDEMPOTENCY_TTL=${DEADLINE_REMINDER_IDEMPOTENCY_TTL}"
echo "DEADLINE_REMINDER_MAX_ATTEMPTS=${DEADLINE_REMINDER_MAX_ATTEMPTS}"
echo "MOCK_EMAIL_FAIL=${MOCK_EMAIL_FAIL}"
echo "MOCK_EMAIL_MESSAGE_ID_PREFIX=${MOCK_EMAIL_MESSAGE_ID_PREFIX}"
echo "NOTE: This command runs the deadline reminder ApplicationRunner in non-web mode."
echo "NOTE: No real email is sent."

cd "$(dirname "$0")/../../backend"

start_epoch_ms="$(date +%s%3N)"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} \
--spring.main.web-application-type=none \
--jobflow.notification.deadline-reminder.runner.enabled=true \
--jobflow.notification.deadline-reminder.runner.mode=${MODE} \
--jobflow.notification.deadline-reminder.scheduler.enabled=false \
--jobflow.analytics.skill-trend.scheduler.enabled=false \
--jobflow.analytics.skill-trend.runner.enabled=false \
--jobflow.analytics.job-skill-index.runner.enabled=false \
--app.notification.email-provider=mock \
--app.notification.mock-email.fail=${MOCK_EMAIL_FAIL} \
--app.notification.mock-email.failure-reason=${MOCK_EMAIL_FAILURE_REASON} \
--app.notification.mock-email.provider-message-id-prefix=${MOCK_EMAIL_MESSAGE_ID_PREFIX} \
--app.notification.deadline-reminder.window=${DEADLINE_REMINDER_WINDOW} \
--app.notification.deadline-reminder.idempotency-ttl=${DEADLINE_REMINDER_IDEMPOTENCY_TTL} \
--app.notification.deadline-reminder.max-attempts=${DEADLINE_REMINDER_MAX_ATTEMPTS}"

end_epoch_ms="$(date +%s%3N)"
duration_ms=$((end_epoch_ms - start_epoch_ms))

echo
echo "Deadline reminder mock load smoke completed."
echo "duration_ms=${duration_ms}"
