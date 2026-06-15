package jobflow.domain.analytics.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import jobflow.domain.analytics.SkillTrend;
import jobflow.domain.skill.SkillCategory;

public record SkillTrendResponse(
        Long skillId,
        String skillName,
        SkillCategory skillCategory,
        LocalDate periodStart,
        long jobCount,
        long requiredCount,
        long preferredCount,
        BigDecimal trendScore
) implements Serializable {

    public static SkillTrendResponse from(SkillTrend skillTrend) {
        return new SkillTrendResponse(
                skillTrend.getSkill().getId(),
                skillTrend.getSkill().getName(),
                skillTrend.getSkill().getCategory(),
                skillTrend.getPeriodStart(),
                skillTrend.getJobCount(),
                skillTrend.getRequiredCount(),
                skillTrend.getPreferredCount(),
                skillTrend.getTrendScore()
        );
    }
}
