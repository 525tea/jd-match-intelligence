package jobflow.domain.notification.digest;

import java.util.List;

public record DailyDigestContent(
        List<DailyDigestJobItem> recommendedJobs,
        List<DailyDigestJobItem> jdMatchJobs,
        List<DailyDigestJobItem> newJobs,
        List<DailyDigestJobItem> deadlineReminderJobs
) {

    public DailyDigestContent {
        recommendedJobs = List.copyOf(recommendedJobs == null ? List.of() : recommendedJobs);
        jdMatchJobs = List.copyOf(jdMatchJobs == null ? List.of() : jdMatchJobs);
        newJobs = List.copyOf(newJobs == null ? List.of() : newJobs);
        deadlineReminderJobs = List.copyOf(deadlineReminderJobs == null ? List.of() : deadlineReminderJobs);
    }

    public boolean isEmpty() {
        return recommendedJobs.isEmpty()
                && jdMatchJobs.isEmpty()
                && newJobs.isEmpty()
                && deadlineReminderJobs.isEmpty();
    }

    public int totalJobCount() {
        return recommendedJobs.size()
                + jdMatchJobs.size()
                + newJobs.size()
                + deadlineReminderJobs.size();
    }
}
