package jobflow.domain.notification.digest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;

public record DailyDigestJobItem(
        Long jobId,
        String title,
        String companyName,
        JobRole role,
        CareerLevel careerLevel,
        BigDecimal score,
        LocalDateTime deadlineAt,
        String originalUrl,
        String reason
) {

    public DailyDigestJobItem {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
    }
}
