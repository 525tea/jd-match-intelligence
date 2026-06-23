package jobflow.domain.project.github;

import java.util.List;
import jobflow.domain.project.analysis.ProjectRepositoryStaticAnalysisImportResult;

public record GitHubRepositoryImportResponse(
        Long userProjectId,
        String repositoryFullName,
        String ref,
        Long analysisId,
        int analysisVersion,
        boolean skipped,
        int candidateSkillCount,
        int savedSkillCount,
        List<String> savedSkillNames,
        List<String> unmappedSkillNames,
        int candidateTagCount,
        int savedTagCount,
        List<String> savedTagCodes,
        List<String> unmappedTagCodes
) {

    public static GitHubRepositoryImportResponse of(
            Long userProjectId,
            String repositoryFullName,
            String ref,
            ProjectRepositoryStaticAnalysisImportResult result
    ) {
        return new GitHubRepositoryImportResponse(
                userProjectId,
                repositoryFullName,
                ref,
                result.analysisId(),
                result.analysisVersion(),
                result.skipped(),
                result.candidateSkillCount(),
                result.savedSkillCount(),
                result.savedSkillNames(),
                result.unmappedSkillNames(),
                result.candidateTagCount(),
                result.savedTagCount(),
                result.savedTagCodes(),
                result.unmappedTagCodes()
        );
    }
}
