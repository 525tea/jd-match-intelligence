package jobflow.domain.job.dto;

import jobflow.domain.job.JobSkill;
import jobflow.domain.job.RequirementType;
import jobflow.domain.skill.SkillCategory;

public record JobSkillResponse(
        Long skillId,
        String name,
        String normalizedName,
        SkillCategory category,
        RequirementType requirementType
) {

    public static JobSkillResponse from(JobSkill jobSkill) {
        return new JobSkillResponse(
                jobSkill.getSkill().getId(),
                jobSkill.getSkill().getName(),
                jobSkill.getSkill().getNormalizedName(),
                jobSkill.getSkill().getCategory(),
                jobSkill.getRequirementType()
        );
    }
}
