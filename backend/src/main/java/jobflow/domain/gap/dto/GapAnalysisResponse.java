package jobflow.domain.gap.dto;

import java.util.List;

public record GapAnalysisResponse(
        Long userProjectId,
        List<Long> userSkillIds,
        List<GapJobMatchResponse> jobMatches
) {
}
