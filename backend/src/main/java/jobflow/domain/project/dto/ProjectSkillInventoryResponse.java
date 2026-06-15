package jobflow.domain.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jobflow.domain.project.AnalysisSource;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectSkill;
import jobflow.domain.skill.Skill;

public record ProjectSkillInventoryResponse(
        Long userProjectId,
        Long analysisId,
        int analysisVersion,
        LocalDateTime analyzedAt,
        boolean latestAnalysis,
        Long skillId,
        String skillName,
        String normalizedName,
        String category,
        AnalysisSource source,
        BigDecimal confidence,
        String evidence
) {

    public static ProjectSkillInventoryResponse from(
            UserProjectAnalysis analysis,
            UserProjectSkill projectSkill
    ) {
        Skill skill = projectSkill.getSkill();

        return new ProjectSkillInventoryResponse(
                analysis.getUserProject().getId(),
                analysis.getId(),
                analysis.getAnalysisVersion(),
                analysis.getAnalyzedAt(),
                true,
                skill.getId(),
                skill.getName(),
                skill.getNormalizedName(),
                skill.getCategory().name(),
                projectSkill.getSource(),
                projectSkill.getConfidence(),
                projectSkill.getEvidence()
        );
    }
}
