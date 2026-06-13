package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GitHubActionsWorkflowExperienceTagParser {

    public boolean supports(WorkflowFileType type) {
        return type == WorkflowFileType.GITHUB_ACTIONS;
    }

    public List<WorkflowExperienceTagCandidate> parse(RepositoryWorkflowFile workflowFile) {
        if (!workflowFile.isSupported() || workflowFile.content().isBlank()) {
            return List.of();
        }

        List<WorkflowExperienceTagCandidate> candidates = new ArrayList<>();
        candidates.add(WorkflowExperienceTagCandidate.of(
                "CI_CD",
                0.90,
                workflowFile.path() + ": GitHub Actions workflow"
        ));

        for (String line : workflowFile.content().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            if (looksSensitiveValue(trimmed)) {
                continue;
            }

            String evidence = workflowFile.path() + ": " + trimmed;
            candidates.addAll(WorkflowEvidenceDictionary.match(evidence));
        }

        return candidates;
    }

    private boolean looksSensitiveValue(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);

        if (normalized.contains("${{ secrets.")) {
            return false;
        }
        return normalized.contains("password:")
                || normalized.contains("token:")
                || normalized.contains("secret:")
                || normalized.contains("private-key:")
                || normalized.contains("access-key:")
                || normalized.contains("client-secret:");
    }
}
