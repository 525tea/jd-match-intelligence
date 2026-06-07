package jobflow.domain.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RemoteType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "job_market_stats")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobMarketStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalyticsPeriodType periodType = AnalyticsPeriodType.MONTHLY;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private JobRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CareerLevel careerLevel;

    @Column(nullable = false, length = 100)
    private String locationRegion = "ALL";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RemoteType remoteType;

    @Column(nullable = false)
    private long jobCount;

    @Column(nullable = false)
    private long openJobCount;

    @Column(nullable = false)
    private long closedJobCount;

    @Column(nullable = false)
    private long expiredJobCount;

    @Column(precision = 5, scale = 2)
    private BigDecimal avgMinExperienceYears;

    @Column(precision = 5, scale = 2)
    private BigDecimal avgMaxExperienceYears;

    @Column(nullable = false)
    private LocalDateTime computedAt;

    public static JobMarketStats create(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            JobRole role,
            CareerLevel careerLevel,
            String locationRegion,
            RemoteType remoteType,
            long jobCount,
            long openJobCount,
            long closedJobCount,
            long expiredJobCount,
            BigDecimal avgMinExperienceYears,
            BigDecimal avgMaxExperienceYears
    ) {
        JobMarketStats stats = new JobMarketStats();
        stats.periodType = periodType;
        stats.periodStart = periodStart;
        stats.role = role;
        stats.careerLevel = careerLevel;
        stats.locationRegion = locationRegion;
        stats.remoteType = remoteType;
        stats.jobCount = jobCount;
        stats.openJobCount = openJobCount;
        stats.closedJobCount = closedJobCount;
        stats.expiredJobCount = expiredJobCount;
        stats.avgMinExperienceYears = avgMinExperienceYears;
        stats.avgMaxExperienceYears = avgMaxExperienceYears;
        return stats;
    }

    @PrePersist
    void prePersist() {
        this.computedAt = LocalDateTime.now();
    }
}
