package jobflow.domain.gap.dto;

public record GapLearningConnectionResponse(
        String missingSkillName,
        String reason
) {
}
