package jobflow.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "jobflow.notification.deadline-reminder.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class DeadlineReminderScheduler {

    private final DeadlineReminderBatchService batchService;

    @Scheduled(
            fixedDelayString = "${jobflow.notification.deadline-reminder.fixed-delay:3600000}",
            initialDelayString = "${jobflow.notification.deadline-reminder.initial-delay:60000}"
    )
    public void sendDeadlineReminders() {
        DeadlineReminderBatchResult sendResult = batchService.sendDueSoonReminders();
        DeadlineReminderBatchResult retryResult = batchService.retryPendingReminders();

        log.info(
                "Deadline reminder scheduler completed. sent={}, failed={}, skipped={}, retrySent={}, retryFailed={}, retrySkipped={}",
                sendResult.sentCount(),
                sendResult.failedCount(),
                sendResult.skippedCount(),
                retryResult.sentCount(),
                retryResult.failedCount(),
                retryResult.skippedCount()
        );
    }
}
