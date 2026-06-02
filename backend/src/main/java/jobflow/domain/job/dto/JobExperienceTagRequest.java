package jobflow.domain.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JobExperienceTagRequest(

        @NotBlank
        @Size(max = 50)
        String tagCode,

        @Size(max = 500)
        String sourcePhrase
) {
}
