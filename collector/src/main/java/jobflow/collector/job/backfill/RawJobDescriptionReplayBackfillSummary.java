package jobflow.collector.job.backfill;

public record RawJobDescriptionReplayBackfillSummary(
        int processedCount,
        int updatedDescriptionCount,
        int unchangedDescriptionCount,
        int updatedDescriptionSectionsCount,
        int updatedRoleCount,
        int skippedCount,
        int failedCount,
        int normalizedSkillJobCount,
        int normalizedExperienceTagJobCount
) {
}
