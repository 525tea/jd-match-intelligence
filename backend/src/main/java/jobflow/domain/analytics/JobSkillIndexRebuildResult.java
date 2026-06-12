package jobflow.domain.analytics;

public record JobSkillIndexRebuildResult(
        int sourceCount,
        int savedCount
) {
}
