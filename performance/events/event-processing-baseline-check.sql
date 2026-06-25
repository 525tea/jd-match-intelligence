SELECT
  'OUTBOX_RELAY_BACKLOG_SUMMARY' AS check_name,
  COUNT(*) AS total_event_count,
  SUM(status = 'PENDING') AS pending_event_count,
  SUM(status = 'PUBLISHED') AS published_event_count,
  SUM(status = 'FAILED') AS failed_event_count,
  SUM(status = 'PENDING' AND retry_count >= 3) AS retry_exhausted_pending_count,
  SUM(status = 'PENDING' AND TIMESTAMPDIFF(SECOND, created_at, NOW(6)) >= 60) AS pending_over_60s_count,
  COALESCE(MAX(CASE
    WHEN status = 'PENDING' THEN TIMESTAMPDIFF(SECOND, created_at, NOW(6))
  END), 0) AS max_pending_age_seconds,
  ROUND(AVG(CASE
    WHEN status = 'PUBLISHED' AND published_at IS NOT NULL
      THEN TIMESTAMPDIFF(MICROSECOND, created_at, published_at) / 1000
  END), 2) AS avg_publish_latency_ms,
  MIN(created_at) AS oldest_event_created_at,
  MAX(created_at) AS newest_event_created_at
FROM outbox_events;

SELECT
  'OUTBOX_STATUS_DETAIL' AS check_name,
  status,
  topic,
  event_type,
  COUNT(*) AS event_count,
  SUM(retry_count) AS total_retry_count,
  MAX(retry_count) AS max_retry_count,
  COALESCE(MAX(CASE
    WHEN status = 'PENDING' THEN TIMESTAMPDIFF(SECOND, created_at, NOW(6))
  END), 0) AS max_pending_age_seconds
FROM outbox_events
GROUP BY status, topic, event_type
ORDER BY status, topic, event_type;

SELECT
  'NOTIFICATION_RETRY_BACKLOG_SUMMARY' AS check_name,
  COUNT(*) AS total_notification_count,
  SUM(status = 'PENDING') AS pending_count,
  SUM(status = 'SENT') AS sent_count,
  SUM(status = 'FAILED') AS failed_count,
  SUM(status = 'PENDING' AND next_retry_at IS NOT NULL AND next_retry_at <= NOW(6)) AS retry_ready_count,
  SUM(status = 'PENDING' AND next_retry_at IS NOT NULL AND next_retry_at > NOW(6)) AS retry_scheduled_count,
  SUM(attempt_count >= max_attempts AND status <> 'SENT') AS retry_exhausted_count,
  COALESCE(MAX(CASE
    WHEN status = 'PENDING' THEN TIMESTAMPDIFF(SECOND, updated_at, NOW(6))
  END), 0) AS max_pending_age_seconds,
  MIN(created_at) AS oldest_notification_created_at,
  MAX(created_at) AS newest_notification_created_at
FROM notification_logs;

SELECT
  'NOTIFICATION_STATUS_DETAIL' AS check_name,
  type,
  status,
  COUNT(*) AS notification_count,
  SUM(attempt_count) AS total_attempt_count,
  MAX(attempt_count) AS max_attempt_count,
  SUM(next_retry_at IS NOT NULL AND next_retry_at <= NOW(6)) AS retry_ready_count,
  SUM(next_retry_at IS NOT NULL AND next_retry_at > NOW(6)) AS retry_scheduled_count
FROM notification_logs
GROUP BY type, status
ORDER BY type, status;

SELECT
  'NOTIFICATION_ATTEMPT_DETAIL' AS check_name,
  provider,
  status,
  COUNT(*) AS attempt_count,
  MIN(attempted_at) AS oldest_attempted_at,
  MAX(attempted_at) AS newest_attempted_at
FROM notification_attempts
GROUP BY provider, status
ORDER BY provider, status;
