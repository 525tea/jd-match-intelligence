package jobflow.domain.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import jobflow.domain.analytics.SkillCooccurrence;

public record SkillCooccurrenceResponse(
        Long baseSkillId,
        String baseSkillName,
        Long coSkillId,
        String coSkillName,
        LocalDate periodStart,
        long cooccurrenceCount,
        long baseSkillJobCount,
        long coSkillJobCount,
        BigDecimal liftScore
) {

    public static SkillCooccurrenceResponse from(SkillCooccurrence cooccurrence) {
        return new SkillCooccurrenceResponse(
                cooccurrence.getBaseSkill().getId(),
                cooccurrence.getBaseSkill().getName(),
                cooccurrence.getCoSkill().getId(),
                cooccurrence.getCoSkill().getName(),
                cooccurrence.getPeriodStart(),
                cooccurrence.getCooccurrenceCount(),
                cooccurrence.getBaseSkillJobCount(),
                cooccurrence.getCoSkillJobCount(),
                cooccurrence.getLiftScore()
        );
    }
}
