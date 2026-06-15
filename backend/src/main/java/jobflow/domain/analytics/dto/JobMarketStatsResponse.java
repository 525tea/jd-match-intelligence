package jobflow.domain.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import jobflow.domain.analytics.JobMarketStats;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;

public record JobMarketStatsResponse(
        JobRole role,
        CareerLevel careerLevel,
        String locationRegion,
        String remoteType,
        LocalDate periodStart,
        long jobCount,
        long openJobCount,
        long closedJobCount,
        long expiredJobCount,
        BigDecimal avgMinExperienceYears,
        BigDecimal avgMaxExperienceYears
) implements Serializable {

    public static JobMarketStatsResponse from(JobMarketStats stats) {
        return new JobMarketStatsResponse(
                stats.getRole(),
                stats.getCareerLevel(),
                stats.getLocationRegion(),
                stats.getRemoteType(),
                stats.getPeriodStart(),
                stats.getJobCount(),
                stats.getOpenJobCount(),
                stats.getClosedJobCount(),
                stats.getExpiredJobCount(),
                stats.getAvgMinExperienceYears(),
                stats.getAvgMaxExperienceYears()
        );
    }
}
