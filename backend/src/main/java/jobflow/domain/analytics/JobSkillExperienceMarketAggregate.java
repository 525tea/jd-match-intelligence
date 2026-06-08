package jobflow.domain.analytics;

import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.Skill;

public record JobSkillExperienceMarketAggregate(
        Skill skill,
        ExperienceTagCode tagCode,
        Long jobCount
) {
}
