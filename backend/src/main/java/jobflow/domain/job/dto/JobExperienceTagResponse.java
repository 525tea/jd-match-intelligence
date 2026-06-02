package jobflow.domain.job.dto;

import jobflow.domain.job.JobExperienceTag;

public record JobExperienceTagResponse(
        String code,
        String name,
        String description,
        String sourcePhrase
) {

    public static JobExperienceTagResponse from(JobExperienceTag jobExperienceTag) {
        return new JobExperienceTagResponse(
                jobExperienceTag.getTagCode().getCode(),
                jobExperienceTag.getTagCode().getName(),
                jobExperienceTag.getTagCode().getDescription(),
                jobExperienceTag.getSourcePhrase()
        );
    }
}
