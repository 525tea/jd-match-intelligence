package jobflow.domain.gap.dto;

import java.math.BigDecimal;
import jobflow.domain.analytics.SkillCooccurrence;

public record GapSkillCooccurrenceEvidenceResponse(
        String baseSkillName,
        String relatedSkillName,
        long cooccurrenceCount,
        long baseSkillJobCount,
        long relatedSkillJobCount,
        BigDecimal liftScore
) {

    public static GapSkillCooccurrenceEvidenceResponse from(SkillCooccurrence cooccurrence) {
        return new GapSkillCooccurrenceEvidenceResponse(
                cooccurrence.getBaseSkill().getName(),
                cooccurrence.getCoSkill().getName(),
                cooccurrence.getCooccurrenceCount(),
                cooccurrence.getBaseSkillJobCount(),
                cooccurrence.getCoSkillJobCount(),
                cooccurrence.getLiftScore()
        );
    }
}
