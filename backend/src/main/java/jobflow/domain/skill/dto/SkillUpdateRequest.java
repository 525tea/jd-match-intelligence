package jobflow.domain.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jobflow.domain.skill.SkillCategory;

public record SkillUpdateRequest(

        @NotBlank
        @Size(max = 100)
        String name,

        @NotBlank
        @Size(max = 100)
        String normalizedName,

        @NotNull
        SkillCategory category
) {
}
