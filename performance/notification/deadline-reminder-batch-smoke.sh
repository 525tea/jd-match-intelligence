#!/usr/bin/env bash

set -euo pipefail

PROFILE="${PROFILE:-local}"
MODE="${MODE:-due-soon}"
CONFIRM_EMAIL_SEND="${CONFIRM_EMAIL_SEND:-false}"
DEADLINE_REMINDER_WINDOW="${DEADLINE_REMINDER_WINDOW:-24h}"
DEADLINE_REMINDER_IDEMPOTENCY_TTL="${DEADLINE_REMINDER_IDEMPOTENCY_TTL:-25h}"
DEADLINE_REMINDER_MAX_ATTEMPTS="${DEADLINE_REMINDER_MAX_ATTEMPTS:-3}"
MAILGUN_BASE_URL="${MAILGUN_BASE_URL:-https://api.mailgun.net}"
MAILGUN_DOMAIN="${MAILGUN_DOMAIN:-}"
MAILGUN_API_KEY="${MAILGUN_API_KEY:-}"
MAILGUN_FROM="${MAILGUN_FROM:-}"

if [[ "${CONFIRM_EMAIL_SEND}" != "true" ]]; then
  echo "This smoke can send real email through Mailgun." >&2
  echo "Set CONFIRM_EMAIL_SEND=true only when Mailgun env values are configured and sending is intended." >&2
  exit 1
fi

if [[ "${MODE}" != "due-soon" && "${MODE}" != "retry" ]]; then
  echo "Unsupported MODE=${MODE}. Use due-soon or retry." >&2
  exit 1
fi

if [[ -z "${MAILGUN_DOMAIN}" || -z "${MAILGUN_API_KEY}" || -z "${MAILGUN_FROM}" ]]; then
  echo "MAILGUN_DOMAIN, MAILGUN_API_KEY, and MAILGUN_FROM are required." >&2
  exit 1
fi

echo "PROFILE=${PROFILE}"
echo "MODE=${MODE}"
echo "DEADLINE_REMINDER_WINDOW=${DEADLINE_REMINDER_WINDOW}"
echo "DEADLINE_REMINDER_IDEMPOTENCY_TTL=${DEADLINE_REMINDER_IDEMPOTENCY_TTL}"
echo "DEADLINE_REMINDER_MAX_ATTEMPTS=${DEADLINE_REMINDER_MAX_ATTEMPTS}"
echo "MAILGUN_BASE_URL=${MAILGUN_BASE_URL}"
echo "MAILGUN_DOMAIN=${MAILGUN_DOMAIN}"
echo "MAILGUN_FROM=${MAILGUN_FROM}"
echo "MAILGUN_API_KEY=(hidden)"
echo "NOTE: This command runs the deadline reminder ApplicationRunner in non-web mode."

export MAILGUN_BASE_URL
export MAILGUN_DOMAIN
export MAILGUN_API_KEY
export MAILGUN_FROM

cd "$(dirname "$0")/../../backend"

./gradlew bootRun \
  --args="--spring.profiles.active=${PROFILE} \
--spring.main.web-application-type=none \
--jobflow.notification.deadline-reminder.runner.enabled=true \
--jobflow.notification.deadline-reminder.runner.mode=${MODE} \
--jobflow.notification.deadline-reminder.scheduler.enabled=false \
--app.notification.deadline-reminder.window=${DEADLINE_REMINDER_WINDOW} \
--app.notification.deadline-reminder.idempotency-ttl=${DEADLINE_REMINDER_IDEMPOTENCY_TTL} \
--app.notification.deadline-reminder.max-attempts=${DEADLINE_REMINDER_MAX_ATTEMPTS}"
