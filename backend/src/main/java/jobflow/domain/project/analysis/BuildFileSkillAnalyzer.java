package jobflow.domain.project.analysis;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BuildFileSkillAnalyzer {

    private final List<BuildFileSkillParser> parsers;

    public BuildFileSkillAnalyzer(List<BuildFileSkillParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public static BuildFileSkillAnalyzer defaultAnalyzer() {
        return new BuildFileSkillAnalyzer(List.of(
                new GradleBuildFileSkillParser(),
                new MavenBuildFileSkillParser(),
                new PackageJsonBuildFileSkillParser()
        ));
    }

    public List<BuildFileSkillCandidate> analyze(List<RepositoryBuildFile> buildFiles) {
        if (buildFiles == null || buildFiles.isEmpty()) {
            return List.of();
        }

        Map<String, BuildFileSkillCandidate> candidatesBySkillName = new LinkedHashMap<>();

        for (RepositoryBuildFile buildFile : buildFiles) {
            parse(buildFile).forEach(candidate -> candidatesBySkillName.merge(
                    candidate.skillName(),
                    candidate,
                    this::chooseHigherConfidence
            ));
        }

        return candidatesBySkillName.values()
                .stream()
                .sorted(Comparator.comparing(BuildFileSkillCandidate::skillName))
                .toList();
    }

    private List<BuildFileSkillCandidate> parse(RepositoryBuildFile buildFile) {
        if (buildFile == null || buildFile.type() == BuildFileType.UNKNOWN) {
            return List.of();
        }

        return parsers.stream()
                .filter(parser -> parser.supports(buildFile.type()))
                .findFirst()
                .map(parser -> parser.parse(buildFile))
                .orElseGet(List::of);
    }

    private BuildFileSkillCandidate chooseHigherConfidence(
            BuildFileSkillCandidate current,
            BuildFileSkillCandidate candidate
    ) {
        if (candidate.confidence().compareTo(current.confidence()) > 0) {
            return candidate;
        }
        return current;
    }
}
