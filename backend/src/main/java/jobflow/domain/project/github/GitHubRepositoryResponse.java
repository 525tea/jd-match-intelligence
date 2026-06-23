package jobflow.domain.project.github;

import java.time.Instant;

public record GitHubRepositoryResponse(
        String owner,
        String name,
        String fullName,
        String defaultBranch,
        boolean privateRepository,
        String htmlUrl,
        String description,
        Instant updatedAt
) {
}
