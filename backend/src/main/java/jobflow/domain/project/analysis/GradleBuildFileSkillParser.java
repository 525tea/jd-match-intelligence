package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleBuildFileSkillParser implements BuildFileSkillParser {

    private static final Pattern PLUGIN_PATTERN = Pattern.compile(
            "\\bid\\s*(?:\\(\\s*)?['\"]([^'\"]+)['\"]"
    );

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile(
            "\\b(?:api|implementation|compileOnly|runtimeOnly|annotationProcessor|testImplementation|testCompileOnly|testRuntimeOnly|kapt)\\s*(?:\\(\\s*)?['\"]([^'\"]+)['\"]"
    );

    @Override
    public boolean supports(BuildFileType type) {
        return type == BuildFileType.GRADLE;
    }

    @Override
    public List<BuildFileSkillCandidate> parse(RepositoryBuildFile buildFile) {
        if (buildFile == null || !buildFile.hasContent()) {
            return List.of();
        }

        List<BuildFileSkillCandidate> candidates = new ArrayList<>();
        for (String line : buildFile.content().lines().toList()) {
            candidates.addAll(parsePlugin(line));
            candidates.addAll(parseDependency(line));
        }
        return candidates;
    }

    private List<BuildFileSkillCandidate> parsePlugin(String line) {
        Matcher matcher = PLUGIN_PATTERN.matcher(line);
        if (!matcher.find()) {
            return List.of();
        }

        String pluginId = matcher.group(1);
        List<BuildFileSkillCandidate> candidates = new ArrayList<>();
        candidates.addAll(BuildDependencySkillDictionary.match("plugin:" + pluginId));
        candidates.addAll(BuildDependencySkillDictionary.match("id:" + pluginId));
        return candidates;
    }

    private List<BuildFileSkillCandidate> parseDependency(String line) {
        Matcher matcher = DEPENDENCY_PATTERN.matcher(line);
        if (!matcher.find()) {
            return List.of();
        }

        String dependency = matcher.group(1);
        return BuildDependencySkillDictionary.match("gradle dependency " + dependency);
    }
}
