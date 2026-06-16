package jobflow.domain.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "jobflow.notification.deadline-reminder.runner",
        name = "enabled",
        havingValue = "true"
)
public class DeadlineReminderBatchRunner implements ApplicationRunner {

    private static final String MODE_RETRY = "retry";

    private final DeadlineReminderBatchService batchService;

    @Value("${jobflow.notification.deadline-reminder.runner.mode:due-soon}")
    private String mode;

    @Override
    public void run(ApplicationArguments args) {
        DeadlineReminderBatchResult result = MODE_RETRY.equalsIgnoreCase(mode)
                ? batchService.retryPendingReminders()
                : batchService.sendDueSoonReminders();

        log.info(
                "Deadline reminder batch runner completed. mode={}, targetCount={}, sentCount={}, failedCount={}, skippedCount={}",
                mode,
                result.targetCount(),
                result.sentCount(),
                result.failedCount(),
                result.skippedCount()
        );
    }
}
