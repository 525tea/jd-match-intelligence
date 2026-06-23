package jobflow.domain.project.github;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GitHubRepositoryImportRequest(
        @NotBlank
        @Size(max = 100)
        String owner,

        @NotBlank
        @Size(max = 100)
        String name,

        @Size(max = 100)
        String ref,

        @Size(max = 500)
        String htmlUrl,

        @Size(max = 1000)
        String description
) {
}
