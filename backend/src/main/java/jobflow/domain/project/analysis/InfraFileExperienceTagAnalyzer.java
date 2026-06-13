package jobflow.domain.project.analysis;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InfraFileExperienceTagAnalyzer {

    private final List<InfraFileExperienceTagParser> parsers;

    public InfraFileExperienceTagAnalyzer(List<InfraFileExperienceTagParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public static InfraFileExperienceTagAnalyzer defaultAnalyzer() {
        return new InfraFileExperienceTagAnalyzer(List.of(
                new DockerfileExperienceTagParser(),
                new DockerComposeExperienceTagParser(),
                new ApplicationConfigExperienceTagParser()
        ));
    }

    public List<InfraExperienceTagCandidate> analyze(List<RepositoryInfraFile> infraFiles) {
        if (infraFiles == null || infraFiles.isEmpty()) {
            return List.of();
        }

        Map<String, InfraExperienceTagCandidate> candidatesByTagCode = new LinkedHashMap<>();

        for (RepositoryInfraFile infraFile : infraFiles) {
            for (InfraExperienceTagCandidate candidate : parse(infraFile)) {
                candidatesByTagCode.merge(
                        candidate.tagCode(),
                        candidate,
                        this::chooseHigherConfidence
                );
            }
        }

        return candidatesByTagCode.values()
                .stream()
                .sorted(Comparator.comparing(InfraExperienceTagCandidate::tagCode))
                .toList();
    }

    private List<InfraExperienceTagCandidate> parse(RepositoryInfraFile infraFile) {
        if (infraFile == null || infraFile.type() == InfraFileType.UNKNOWN) {
            return List.of();
        }

        return parsers.stream()
                .filter(parser -> parser.supports(infraFile.type()))
                .findFirst()
                .map(parser -> parser.parse(infraFile))
                .orElse(List.of());
    }

    private InfraExperienceTagCandidate chooseHigherConfidence(
            InfraExperienceTagCandidate current,
            InfraExperienceTagCandidate candidate
    ) {
        if (candidate.confidence().compareTo(current.confidence()) > 0) {
            return candidate;
        }
        return current;
    }
}
