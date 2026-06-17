package jobflow.domain.notification.digest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DailyDigestSectionAssembler {

    static final int RECOMMENDED_LIMIT = 3;
    static final int JD_MATCH_LIMIT = 3;
    static final int NEW_JOB_LIMIT = 1;
    static final int DEADLINE_REMINDER_LIMIT = 3;

    public DailyDigestContent assemble(
            List<DailyDigestJobItem> recommendedCandidates,
            List<DailyDigestJobItem> jdMatchCandidates,
            List<DailyDigestJobItem> newJobCandidates,
            List<DailyDigestJobItem> deadlineReminderCandidates
    ) {
        Set<Long> selectedJobIds = new LinkedHashSet<>();

        List<DailyDigestJobItem> recommendedJobs = select(
                recommendedCandidates,
                selectedJobIds,
                RECOMMENDED_LIMIT
        );
        List<DailyDigestJobItem> jdMatchJobs = select(
                jdMatchCandidates,
                selectedJobIds,
                JD_MATCH_LIMIT
        );
        List<DailyDigestJobItem> newJobs = select(
                newJobCandidates,
                selectedJobIds,
                NEW_JOB_LIMIT
        );
        List<DailyDigestJobItem> deadlineReminderJobs = select(
                deadlineReminderCandidates,
                selectedJobIds,
                DEADLINE_REMINDER_LIMIT
        );

        return new DailyDigestContent(
                recommendedJobs,
                jdMatchJobs,
                newJobs,
                deadlineReminderJobs
        );
    }

    private List<DailyDigestJobItem> select(
            List<DailyDigestJobItem> candidates,
            Set<Long> selectedJobIds,
            int limit
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        return candidates.stream()
                .filter(candidate -> candidate != null)
                .filter(candidate -> selectedJobIds.add(candidate.jobId()))
                .limit(limit)
                .toList();
    }
}
