package jobflow.domain.project.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jobflow.domain.project.AnalysisSource;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.skill.ExperienceTagCode;

public record ProjectExperienceTagInventoryResponse(
        Long userProjectId,
        Long analysisId,
        int analysisVersion,
        LocalDateTime analyzedAt,
        boolean latestAnalysis,
        String tagCode,
        String tagName,
        String description,
        AnalysisSource source,
        BigDecimal confidence,
        String evidence
) {

    public static ProjectExperienceTagInventoryResponse from(
            UserProjectAnalysis analysis,
            UserProjectExperienceTag projectExperienceTag
    ) {
        ExperienceTagCode tagCode = projectExperienceTag.getTagCode();

        return new ProjectExperienceTagInventoryResponse(
                analysis.getUserProject().getId(),
                analysis.getId(),
                analysis.getAnalysisVersion(),
                analysis.getAnalyzedAt(),
                true,
                tagCode.getCode(),
                tagCode.getName(),
                tagCode.getDescription(),
                AnalysisSource.STATIC,
                projectExperienceTag.getConfidence(),
                projectExperienceTag.getEvidence()
        );
    }
}
