package jobflow.collector.job.backfill;

public record RawJobDescriptionReplayBackfillSummary(
        int processedCount,
        int updatedDescriptionCount,
        int unchangedDescriptionCount,
        int skippedCount,
        int failedCount,
        int normalizedSkillJobCount,
        int normalizedExperienceTagJobCount
) {
}
