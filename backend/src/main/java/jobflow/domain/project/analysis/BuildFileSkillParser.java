package jobflow.domain.project.analysis;

import java.util.List;

public interface BuildFileSkillParser {

    boolean supports(BuildFileType type);

    List<BuildFileSkillCandidate> parse(RepositoryBuildFile buildFile);
}
