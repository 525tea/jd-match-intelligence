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
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.Skill;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "skill_experience_market")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillExperienceMarket {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_code", nullable = false)
    private ExperienceTagCode tagCode;

    @Column(nullable = false)
    private long jobCount;

    @Column(nullable = false)
    private long skillJobCount;

    @Column(nullable = false)
    private long tagJobCount;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal liftScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime computedAt;

    public static SkillExperienceMarket create(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Skill skill,
            ExperienceTagCode tagCode,
            long jobCount,
            long skillJobCount,
            long tagJobCount,
            BigDecimal liftScore
    ) {
        SkillExperienceMarket market = new SkillExperienceMarket();
        market.periodType = periodType;
        market.periodStart = periodStart;
        market.skill = skill;
        market.tagCode = tagCode;
        market.jobCount = jobCount;
        market.skillJobCount = skillJobCount;
        market.tagJobCount = tagJobCount;
        market.liftScore = liftScore;
        return market;
    }

    @PrePersist
    void prePersist() {
        this.computedAt = LocalDateTime.now();
    }
}
