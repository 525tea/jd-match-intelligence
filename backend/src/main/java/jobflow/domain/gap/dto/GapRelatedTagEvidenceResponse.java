package jobflow.domain.gap.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import jobflow.domain.analytics.SkillExperienceMarket;
import jobflow.domain.skill.ExperienceTagCode;

public record GapRelatedTagEvidenceResponse(
        String skillName,
        String tagCode,
        String tagName,
        String tagDescription,
        long jobCount,
        long skillJobCount,
        long tagJobCount,
        BigDecimal liftScore
) implements Serializable {

    public static GapRelatedTagEvidenceResponse from(SkillExperienceMarket market) {
        ExperienceTagCode tagCode = market.getTagCode();

        return new GapRelatedTagEvidenceResponse(
                market.getSkill().getName(),
                tagCode.getCode(),
                tagCode.getName(),
                tagCode.getDescription(),
                market.getJobCount(),
                market.getSkillJobCount(),
                market.getTagJobCount(),
                market.getLiftScore()
        );
    }
}
