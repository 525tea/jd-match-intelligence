package jobflow.domain.project.analysis;

import java.math.BigDecimal;
import java.util.Objects;

public record WorkflowExperienceTagCandidate(
        String tagCode,
        BigDecimal confidence,
        String evidence
) {

    public WorkflowExperienceTagCandidate {
        Objects.requireNonNull(tagCode, "tagCode must not be null");
        Objects.requireNonNull(confidence, "confidence must not be null");
        Objects.requireNonNull(evidence, "evidence must not be null");
    }

    public static WorkflowExperienceTagCandidate of(
            String tagCode,
            double confidence,
            String evidence
    ) {
        return new WorkflowExperienceTagCandidate(
                tagCode,
                BigDecimal.valueOf(confidence),
                evidence
        );
    }
}
