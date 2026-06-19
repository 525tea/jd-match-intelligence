package jobflow.domain.job.dto;

import java.util.List;

public record JobCanonicalGroupResponse(
        String canonicalFingerprint,
        Long representativeJobId,
        String representativeApplyUrl,
        int duplicateCount,
        List<JobCanonicalGroupItemResponse> jobs
) {
}
