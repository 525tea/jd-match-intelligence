package jobflow.domain.analytics;

import jobflow.domain.skill.Skill;

public record JobSkillCooccurrenceAggregate(
        Skill baseSkill,
        Skill coSkill,
        Long cooccurrenceCount
) {
}
