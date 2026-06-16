package jobflow.domain.notification;

public record DailyDigestBatchResult(
        int targetCount,
        int sentCount,
        int failedCount,
        int skippedCount
) {

    public static DailyDigestBatchResult empty() {
        return new DailyDigestBatchResult(0, 0, 0, 0);
    }
}
