package jobflow.domain.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobStatus;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.userjob.UserJobRepository;
import jobflow.domain.userjob.UserJobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineReminderBatchService {

    private static final NotificationType TYPE = NotificationType.DEADLINE_REMINDER;

    private final UserJobRepository userJobRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationAttemptRepository notificationAttemptRepository;
    private final DeadlineReminderIdempotencyService idempotencyService;
    private final DeadlineReminderEmailRenderer emailRenderer;
    private final EmailSender emailSender;
    private final DeadlineReminderProperties properties;
    private final Clock clock;

    @Transactional
    public DeadlineReminderBatchResult sendDueSoonReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadlineUntil = now.plus(properties.window());
        List<DeadlineReminderTarget> targets = userJobRepository.findDeadlineReminderTargets(
                UserJobStatus.SAVED,
                JobStatus.OPEN,
                now,
                deadlineUntil,
                TYPE
        );

        BatchCounter counter = new BatchCounter(targets.size());
        for (DeadlineReminderTarget target : targets) {
            sendNewTarget(target, now, counter);
        }
        DeadlineReminderBatchResult result = counter.toResult();
        log.info(
                "Deadline reminder batch completed. targetCount={}, sentCount={}, failedCount={}, skippedCount={}",
                result.targetCount(),
                result.sentCount(),
                result.failedCount(),
                result.skippedCount()
        );
        return result;
    }

    @Transactional
    public DeadlineReminderBatchResult retryPendingReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NotificationLog> retryableLogs =
                notificationLogRepository.findByTypeAndStatusAndNextRetryAtLessThanEqual(
                        TYPE,
                        NotificationStatus.PENDING,
                        now
                );

        BatchCounter counter = new BatchCounter(retryableLogs.size());
        for (NotificationLog notificationLog : retryableLogs) {
            if (!notificationLog.canRetry(now)) {
                counter.skipped++;
                continue;
            }
            DeadlineReminderTarget target = toTarget(notificationLog);
            if (!idempotencyService.acquire(target.userId(), target.jobId())) {
                counter.skipped++;
                continue;
            }
            sendExistingLog(notificationLog, target, now, counter);
        }
        return counter.toResult();
    }

    private void sendNewTarget(
            DeadlineReminderTarget target,
            LocalDateTime now,
            BatchCounter counter
    ) {
        if (!idempotencyService.acquire(target.userId(), target.jobId())) {
            counter.skipped++;
            return;
        }
        if (notificationLogRepository.existsByUserIdAndJobIdAndType(target.userId(), target.jobId(), TYPE)) {
            counter.skipped++;
            return;
        }

        User user = userRepository.getReferenceById(target.userId());
        Job job = jobRepository.getReferenceById(target.jobId());
        NotificationLog notificationLog = notificationLogRepository.save(NotificationLog.create(
                user,
                job,
                TYPE,
                properties.maxAttempts(),
                now
        ));
        sendExistingLog(notificationLog, target, now, counter);
    }

    private void sendExistingLog(
            NotificationLog notificationLog,
            DeadlineReminderTarget target,
            LocalDateTime now,
            BatchCounter counter
    ) {
        int attemptNumber = notificationLog.nextAttemptNumber();
        EmailSendResult sendResult = emailSender.send(emailRenderer.render(target));
        if (sendResult.success()) {
            notificationAttemptRepository.save(NotificationAttempt.sent(
                    notificationLog,
                    attemptNumber,
                    sendResult.provider(),
                    sendResult.providerMessageId(),
                    now
            ));
            notificationLog.markSent(now);
            counter.sent++;
            return;
        }

        notificationAttemptRepository.save(NotificationAttempt.failed(
                notificationLog,
                attemptNumber,
                sendResult.provider(),
                sendResult.failureReason(),
                now
        ));
        notificationLog.markFailed(now, nextRetryAt(now, attemptNumber));
        idempotencyService.release(target.userId(), target.jobId());
        counter.failed++;
    }

    private LocalDateTime nextRetryAt(LocalDateTime now, int attemptNumber) {
        long delayMinutes = (long) Math.pow(2, Math.max(0, attemptNumber - 1)) * 10L;
        return now.plusMinutes(delayMinutes);
    }

    private DeadlineReminderTarget toTarget(NotificationLog notificationLog) {
        User user = notificationLog.getUser();
        Job job = notificationLog.getJob();
        return new DeadlineReminderTarget(
                user.getId(),
                user.getEmail(),
                user.getName(),
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getDeadlineAt(),
                job.getOriginalUrl()
        );
    }

    private static class BatchCounter {

        private final int targetCount;
        private int sent;
        private int failed;
        private int skipped;

        private BatchCounter(int targetCount) {
            this.targetCount = targetCount;
        }

        private DeadlineReminderBatchResult toResult() {
            return new DeadlineReminderBatchResult(targetCount, sent, failed, skipped);
        }
    }
}
