package jobflow.domain.project.analysis;

import java.math.BigDecimal;
import java.util.Objects;

public record BuildFileSkillCandidate(
        String skillName,
        String evidence,
        BigDecimal confidence
) {

    public BuildFileSkillCandidate {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (evidence == null || evidence.isBlank()) {
            throw new IllegalArgumentException("evidence must not be blank");
        }
        Objects.requireNonNull(confidence, "confidence must not be null");
    }

    public static BuildFileSkillCandidate of(
            String skillName,
            String evidence,
            double confidence
    ) {
        return new BuildFileSkillCandidate(
                skillName,
                evidence,
                BigDecimal.valueOf(confidence)
        );
    }
}
