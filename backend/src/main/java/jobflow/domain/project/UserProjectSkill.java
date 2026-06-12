package jobflow.domain.project;

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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import jobflow.domain.skill.Skill;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_project_skills")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProjectSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private UserProjectAnalysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(length = 500)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisSource source = AnalysisSource.STATIC;

    public static UserProjectSkill create(
            UserProjectAnalysis analysis,
            Skill skill,
            BigDecimal confidence,
            String evidence,
            AnalysisSource source
    ) {
        UserProjectSkill projectSkill = new UserProjectSkill();
        projectSkill.analysis = Objects.requireNonNull(analysis, "analysis must not be null");
        projectSkill.skill = Objects.requireNonNull(skill, "skill must not be null");
        projectSkill.confidence = confidence;
        projectSkill.evidence = evidence;
        projectSkill.source = Objects.requireNonNull(source, "source must not be null");
        return projectSkill;
    }
}
