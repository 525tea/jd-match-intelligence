package jobflow.global.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record JobFlowCacheProperties(
        boolean enabled,
        Duration jobSearchTtl,
        Duration trendTtl,
        Duration gapAnalysisTtl,
        Duration jdMatchTtl,
        Duration recommendationTtl,
        Duration projectInventoryTtl
) {

    private static final Duration DEFAULT_JOB_SEARCH_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_TREND_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_GAP_ANALYSIS_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_JD_MATCH_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_RECOMMENDATION_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_PROJECT_INVENTORY_TTL = Duration.ofHours(1);

    public JobFlowCacheProperties {
        jobSearchTtl = jobSearchTtl == null ? DEFAULT_JOB_SEARCH_TTL : jobSearchTtl;
        trendTtl = trendTtl == null ? DEFAULT_TREND_TTL : trendTtl;
        gapAnalysisTtl = gapAnalysisTtl == null ? DEFAULT_GAP_ANALYSIS_TTL : gapAnalysisTtl;
        jdMatchTtl = jdMatchTtl == null ? DEFAULT_JD_MATCH_TTL : jdMatchTtl;
        recommendationTtl = recommendationTtl == null ? DEFAULT_RECOMMENDATION_TTL : recommendationTtl;
        projectInventoryTtl = projectInventoryTtl == null ? DEFAULT_PROJECT_INVENTORY_TTL : projectInventoryTtl;
    }
}
