package jobflow.domain.skill.dto;

import jobflow.domain.skill.Skill;
import jobflow.domain.skill.SkillCategory;

public record SkillResponse(
        Long id,
        String name,
        String normalizedName,
        SkillCategory category
) {

    public static SkillResponse from(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getNormalizedName(),
                skill.getCategory()
        );
    }
}
