package jobflow.domain.job.dto;

public record JobDescriptionSectionResponse(
        String type,
        String title,
        String body
) {
}
