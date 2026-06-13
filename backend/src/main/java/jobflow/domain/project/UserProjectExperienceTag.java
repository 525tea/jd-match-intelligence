package jobflow.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import jobflow.domain.skill.ExperienceTagCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "user_project_experience_tags")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProjectExperienceTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private UserProjectAnalysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_code", nullable = false)
    private ExperienceTagCode tagCode;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(length = 500)
    private String evidence;

    public static UserProjectExperienceTag create(
            UserProjectAnalysis analysis,
            ExperienceTagCode tagCode,
            BigDecimal confidence,
            String evidence
    ) {
        UserProjectExperienceTag projectExperienceTag = new UserProjectExperienceTag();
        projectExperienceTag.analysis = Objects.requireNonNull(analysis, "analysis must not be null");
        projectExperienceTag.tagCode = Objects.requireNonNull(tagCode, "tagCode must not be null");
        projectExperienceTag.confidence = confidence;
        projectExperienceTag.evidence = evidence;
        return projectExperienceTag;
    }
}
