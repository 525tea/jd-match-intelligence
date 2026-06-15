package jobflow.domain.analytics;

public record JobSkillIndexRebuiltEvent(
        int sourceCount,
        int savedCount
) {
}
