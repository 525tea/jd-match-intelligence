package jobflow.domain.project.analysis;

import java.math.BigDecimal;
import java.util.Objects;

public record InfraExperienceTagCandidate(
        String tagCode,
        BigDecimal confidence,
        String evidence
) {

    public InfraExperienceTagCandidate {
        Objects.requireNonNull(tagCode, "tagCode must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
    }

    public static InfraExperienceTagCandidate of(
            String tagCode,
            double confidence,
            String evidence
    ) {
        return new InfraExperienceTagCandidate(
                tagCode,
                BigDecimal.valueOf(confidence),
                evidence
        );
    }
}
