package jobflow.domain.project.analysis;

import java.util.List;

public interface InfraFileExperienceTagParser {

    boolean supports(InfraFileType type);

    List<InfraExperienceTagCandidate> parse(RepositoryInfraFile infraFile);
}
