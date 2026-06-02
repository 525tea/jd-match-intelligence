package jobflow.domain.job.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jobflow.domain.job.RequirementType;

public record JobSkillRequest(

        @NotNull
        @Positive
        Long skillId,

        @NotNull
        RequirementType requirementType
) {
}
