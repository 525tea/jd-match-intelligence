package jobflow.domain.gap.dto;

import java.util.List;

public record GapJobMatchEvidenceResponse(
        long addedJobs,
        List<GapSkillCooccurrenceEvidenceResponse> cooccurrences,
        List<GapRelatedTagEvidenceResponse> relatedTags,
        List<GapLearningConnectionResponse> learningConnections
) {

    public static GapJobMatchEvidenceResponse empty() {
        return new GapJobMatchEvidenceResponse(
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
