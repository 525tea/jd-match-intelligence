package jobflow.domain.gap.dto;

import java.io.Serializable;

public record GapLearningConnectionResponse(
        String missingSkillName,
        String reason
) implements Serializable {
}
