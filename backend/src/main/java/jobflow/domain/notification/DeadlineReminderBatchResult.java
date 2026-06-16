package jobflow.domain.notification;

public record DeadlineReminderBatchResult(
        int targetCount,
        int sentCount,
        int failedCount,
        int skippedCount
) {

    public static DeadlineReminderBatchResult empty() {
        return new DeadlineReminderBatchResult(0, 0, 0, 0);
    }
}
