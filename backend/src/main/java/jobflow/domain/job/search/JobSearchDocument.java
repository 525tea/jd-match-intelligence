package jobflow.domain.job.search;

import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;

public record JobSearchDocument(
        @Id String id,
        String source,
        String externalId,
        String canonicalFingerprint,
        String title,
        String companyName,
        String description,
        String role,
        String roleDetail,
        String careerLevel,
        String employmentType,
        String industry,
        String locationCountry,
        String locationRegion,
        String locationCity,
        String remoteType,
        LocalDateTime deadlineAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
