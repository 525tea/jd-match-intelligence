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
@Table(name = "skill_cooccurrence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillCooccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalyticsPeriodType periodType = AnalyticsPeriodType.MONTHLY;

    @Column(nullable = false)
    private LocalDate periodStart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_skill_id", nullable = false)
    private Skill baseSkill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "co_skill_id", nullable = false)
    private Skill coSkill;

    @Column(nullable = false)
    private long cooccurrenceCount;

    @Column(nullable = false)
    private long baseSkillJobCount;

    @Column(nullable = false)
    private long coSkillJobCount;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal liftScore = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime computedAt;

    public static SkillCooccurrence create(
            AnalyticsPeriodType periodType,
            LocalDate periodStart,
            Skill baseSkill,
            Skill coSkill,
            long cooccurrenceCount,
            long baseSkillJobCount,
            long coSkillJobCount,
            BigDecimal liftScore
    ) {
        SkillCooccurrence skillCooccurrence = new SkillCooccurrence();
        skillCooccurrence.periodType = periodType;
        skillCooccurrence.periodStart = periodStart;
        skillCooccurrence.baseSkill = baseSkill;
        skillCooccurrence.coSkill = coSkill;
        skillCooccurrence.cooccurrenceCount = cooccurrenceCount;
        skillCooccurrence.baseSkillJobCount = baseSkillJobCount;
        skillCooccurrence.coSkillJobCount = coSkillJobCount;
        skillCooccurrence.liftScore = liftScore;
        return skillCooccurrence;
    }

    @PrePersist
    void prePersist() {
        this.computedAt = LocalDateTime.now();
    }
}
