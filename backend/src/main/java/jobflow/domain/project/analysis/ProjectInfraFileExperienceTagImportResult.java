package jobflow.domain.project.analysis;

import java.util.List;

public record ProjectInfraFileExperienceTagImportResult(
        Long analysisId,
        int analysisVersion,
        int candidateTagCount,
        int savedTagCount,
        List<String> savedTagCodes,
        List<String> unmappedTagCodes
) {

    public ProjectInfraFileExperienceTagImportResult {
        savedTagCodes = List.copyOf(savedTagCodes);
        unmappedTagCodes = List.copyOf(unmappedTagCodes);
    }
}
