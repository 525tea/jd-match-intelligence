package jobflow.collector.job.backfill;

public record RealJobNormalizationBackfillSummary(
        int processedCount,
        int roleUpdatedCount,
        int normalizedSkillJobCount,
        int normalizedExperienceTagJobCount
) {
}
