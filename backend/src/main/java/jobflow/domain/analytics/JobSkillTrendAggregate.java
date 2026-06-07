package jobflow.domain.analytics;

import jobflow.domain.skill.Skill;

public record JobSkillTrendAggregate(
        Skill skill,
        long jobCount,
        long requiredCount,
        long preferredCount
) {
}
