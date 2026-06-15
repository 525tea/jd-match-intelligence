package jobflow.domain.matching.dto;

import java.io.Serializable;
import jobflow.domain.skill.ExperienceTagCode;

public record JdMatchExperienceTagResponse(
        String code,
        String name,
        String description
) implements Serializable {

    public static JdMatchExperienceTagResponse from(ExperienceTagCode tagCode) {
        return new JdMatchExperienceTagResponse(
                tagCode.getCode(),
                tagCode.getName(),
                tagCode.getDescription()
        );
    }
}
