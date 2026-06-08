package jobflow.domain.analytics;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;

public record JobMarketStatsAggregate(
        JobRole role,
        CareerLevel careerLevel,
        String locationRegion,
        RemoteType remoteType,
        Long jobCount,
        Long openJobCount,
        Long closedJobCount,
        Long expiredJobCount,
        Double avgMinExperienceYears,
        Double avgMaxExperienceYears
) {
}
