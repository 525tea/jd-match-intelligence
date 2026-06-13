package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.List;

public class ApplicationConfigExperienceTagParser implements InfraFileExperienceTagParser {

    @Override
    public boolean supports(InfraFileType type) {
        return type == InfraFileType.APPLICATION_YAML
                || type == InfraFileType.APPLICATION_PROPERTIES;
    }

    @Override
    public List<InfraExperienceTagCandidate> parse(RepositoryInfraFile infraFile) {
        if (infraFile.content().isBlank()) {
            return List.of();
        }

        List<InfraExperienceTagCandidate> candidates = new ArrayList<>();

        for (String line : infraFile.content().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            if (looksSensitive(trimmed)) {
                continue;
            }

            String evidence = infraFile.path() + ": " + trimmed;
            candidates.addAll(InfraEvidenceDictionary.match(evidence));
        }

        return candidates;
    }

    private boolean looksSensitive(String line) {
        String lower = line.toLowerCase();
        return lower.contains("password")
                || lower.contains("secret")
                || lower.contains("token")
                || lower.contains("private-key")
                || lower.contains("access-key")
                || lower.contains("client-secret");
    }
}
