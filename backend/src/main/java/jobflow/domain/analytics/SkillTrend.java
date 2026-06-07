package jobflow.domain.analytics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import jobflow.domain.skill.Skill;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "skill_trends")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalyticsPeriodType periodType = AnalyticsPeriodType.MONTHLY;

    @Column(nullable = false)
    private LocalDate periodStart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false)
    private long jobCount;

    @Column(nullable = false)
    private long requiredCount;

    @Column(nullable = false)
    private long preferredCount;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal trendScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime computedAt;

    public static SkillTrend create(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Skill skill,
            long jobCount,
            long requiredCount,
            long preferredCount,
            BigDecimal trendScore
    ) {
        SkillTrend skillTrend = new SkillTrend();
        skillTrend.periodType = periodType;
        skillTrend.periodStart = periodStart;
        skillTrend.skill = skill;
        skillTrend.jobCount = jobCount;
        skillTrend.requiredCount = requiredCount;
        skillTrend.preferredCount = preferredCount;
        skillTrend.trendScore = trendScore;
        return skillTrend;
    }

    @PrePersist
    void prePersist() {
        this.computedAt = LocalDateTime.now();
    }
}
