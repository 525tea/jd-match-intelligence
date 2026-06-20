package jobflow.collector.normalization;

public record NormalizationCandidateCollectionSummary(
        int processedJobCount,
        int skillAliasCandidateCount,
        int sectionLabelCandidateCount
) {
}
