package jobflow.domain.gap.dto;

import java.util.List;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;

public record GapAnalysisResponse(
        Long userProjectId,
        List<Long> userSkillIds,
        List<JobSkillMatchResponse> jobMatches
) {
}
