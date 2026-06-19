package jobflow.domain.job.dto;

import java.time.LocalDateTime;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobStatus;

public record JobCanonicalGroupItemResponse(
        Long id,
        String source,
        String externalId,
        String title,
        String companyName,
        String applyUrl,
        JobStatus status,
        LocalDateTime deadlineAt,
        boolean representative
) {

    public static JobCanonicalGroupItemResponse of(
            Job job,
            String applyUrl,
            boolean representative
    ) {
        return new JobCanonicalGroupItemResponse(
                job.getId(),
                job.getSource(),
                job.getExternalId(),
                job.getTitle(),
                job.getCompanyName(),
                applyUrl,
                job.getStatus(),
                job.getDeadlineAt(),
                representative
        );
    }
}
