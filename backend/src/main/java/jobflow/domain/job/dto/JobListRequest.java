package jobflow.domain.job.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;

public record JobListRequest(
        @Min(0)
        Integer page,

        @Min(1)
        @Max(100)
        Integer size,

        JobStatus status,
        JobRole role,
        CareerLevel careerLevel,
        String locationRegion,
        RemoteType remoteType
) {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public int pageOrDefault() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_SIZE : size;
    }

    public String normalizedLocationRegion() {
        return locationRegion == null || locationRegion.isBlank() ? null : locationRegion.trim();
    }
}
