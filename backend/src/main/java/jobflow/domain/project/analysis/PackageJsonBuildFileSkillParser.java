package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageJsonBuildFileSkillParser implements BuildFileSkillParser {

    private static final Pattern PACKAGE_NAME_PATTERN = Pattern.compile(
            "\"([@A-Za-z0-9._/-]+)\"\\s*:"
    );

    @Override
    public boolean supports(BuildFileType type) {
        return type == BuildFileType.PACKAGE_JSON;
    }

    @Override
    public List<BuildFileSkillCandidate> parse(RepositoryBuildFile buildFile) {
        if (buildFile == null || !buildFile.hasContent()) {
            return List.of();
        }

        Matcher matcher = PACKAGE_NAME_PATTERN.matcher(buildFile.content());
        List<BuildFileSkillCandidate> candidates = new ArrayList<>();

        while (matcher.find()) {
            String packageName = matcher.group(1);
            candidates.addAll(BuildDependencySkillDictionary.match("package.json dependency " + packageName));
        }

        return candidates;
    }
}
