package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.List;

public class DockerfileExperienceTagParser implements InfraFileExperienceTagParser {

    @Override
    public boolean supports(InfraFileType type) {
        return type == InfraFileType.DOCKERFILE;
    }

    @Override
    public List<InfraExperienceTagCandidate> parse(RepositoryInfraFile infraFile) {
        if (infraFile.content().isBlank()) {
            return List.of();
        }

        List<InfraExperienceTagCandidate> candidates = new ArrayList<>();
        candidates.add(InfraExperienceTagCandidate.of(
                "CLOUD_INFRA",
                0.80,
                infraFile.path() + ": Dockerfile container build/runtime configuration"
        ));

        for (String line : infraFile.content().split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }

            String evidence = infraFile.path() + ": " + trimmed;
            candidates.addAll(InfraEvidenceDictionary.match(evidence));
        }

        return candidates;
    }
}
