-- Make pending mock deadline reminder notifications immediately retryable.
--
-- This is only for the performance database recovery scenario.
-- It touches the NOTIFICATION_MOCK_LOAD fixture created by
-- performance/sql/deadline-reminder-mock-load-fixture.sql.

SET @source = 'NOTIFICATION_MOCK_LOAD' COLLATE utf8mb4_unicode_ci;

UPDATE notification_logs nl
    JOIN jobs j ON j.id = nl.job_id
SET nl.next_retry_at = NOW(6),
    nl.updated_at = NOW(6)
WHERE j.source = @source
  AND nl.type = 'DEADLINE_REMINDER'
  AND nl.status = 'PENDING';

SELECT
    'DEADLINE_REMINDER_RETRY_READY' AS check_name,
    COUNT(*) AS pending_count,
    SUM(nl.next_retry_at <= NOW(6)) AS retry_ready_count,
    MIN(nl.next_retry_at) AS oldest_next_retry_at,
    MAX(nl.next_retry_at) AS newest_next_retry_at
FROM notification_logs nl
    JOIN jobs j ON j.id = nl.job_id
WHERE j.source = @source
  AND nl.type = 'DEADLINE_REMINDER'
  AND nl.status = 'PENDING';
