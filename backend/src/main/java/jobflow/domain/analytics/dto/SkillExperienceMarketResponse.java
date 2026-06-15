package jobflow.domain.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import jobflow.domain.analytics.SkillExperienceMarket;

public record SkillExperienceMarketResponse(
        Long skillId,
        String skillName,
        String tagCode,
        String tagName,
        LocalDate periodStart,
        long jobCount,
        long skillJobCount,
        long tagJobCount,
        BigDecimal liftScore
) implements Serializable {

    public static SkillExperienceMarketResponse from(SkillExperienceMarket market) {
        return new SkillExperienceMarketResponse(
                market.getSkill().getId(),
                market.getSkill().getName(),
                market.getTagCode().getCode(),
                market.getTagCode().getName(),
                market.getPeriodStart(),
                market.getJobCount(),
                market.getSkillJobCount(),
                market.getTagJobCount(),
                market.getLiftScore()
        );
    }
}
