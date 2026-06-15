package jobflow.domain.gap.dto;

import java.io.Serializable;
import java.util.List;

public record GapAnalysisResponse(
        Long userProjectId,
        List<Long> userSkillIds,
        List<GapJobMatchResponse> jobMatches
) implements Serializable {
}
