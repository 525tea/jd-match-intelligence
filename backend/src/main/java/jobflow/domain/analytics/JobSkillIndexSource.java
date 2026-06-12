package jobflow.domain.analytics;

import jobflow.domain.job.Job;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.Skill;

public record JobSkillIndexSource(
        Job job,
        Skill skill,
        RequirementType requirementType
) {
}
