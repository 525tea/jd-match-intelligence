package jobflow.domain.project.analysis;

import java.util.List;

public record ProjectRepositoryStaticAnalysisImportResult(
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

    public ProjectRepositoryStaticAnalysisImportResult {
        savedSkillNames = List.copyOf(savedSkillNames);
        unmappedSkillNames = List.copyOf(unmappedSkillNames);
        savedTagCodes = List.copyOf(savedTagCodes);
        unmappedTagCodes = List.copyOf(unmappedTagCodes);
    }

    public static ProjectRepositoryStaticAnalysisImportResult skipped(
            Long analysisId,
            int analysisVersion
    ) {
        return new ProjectRepositoryStaticAnalysisImportResult(
                analysisId,
                analysisVersion,
                true,
                0,
                0,
                List.of(),
                List.of(),
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
