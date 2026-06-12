package jobflow.domain.project.analysis;

import java.util.List;

public record ProjectBuildFileSkillImportResult(
        Long analysisId,
        int analysisVersion,
        int candidateSkillCount,
        int savedSkillCount,
        List<String> savedSkillNames,
        List<String> unmappedSkillNames
) {

    public ProjectBuildFileSkillImportResult {
        savedSkillNames = List.copyOf(savedSkillNames);
        unmappedSkillNames = List.copyOf(unmappedSkillNames);
    }
}
