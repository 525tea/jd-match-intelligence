package jobflow.domain.matching.dto;

import jobflow.domain.skill.ExperienceTagCode;

public record JdMatchExperienceTagResponse(
        String code,
        String name,
        String description
) {

    public static JdMatchExperienceTagResponse from(ExperienceTagCode tagCode) {
        return new JdMatchExperienceTagResponse(
                tagCode.getCode(),
                tagCode.getName(),
                tagCode.getDescription()
        );
    }
}
