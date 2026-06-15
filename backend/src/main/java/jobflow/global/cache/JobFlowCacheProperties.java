package jobflow.global.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record JobFlowCacheProperties(
        Duration trendTtl,
        Duration gapAnalysisTtl,
        Duration projectInventoryTtl
) {

    private static final Duration DEFAULT_TREND_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_GAP_ANALYSIS_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_PROJECT_INVENTORY_TTL = Duration.ofHours(1);

    public JobFlowCacheProperties {
        trendTtl = trendTtl == null ? DEFAULT_TREND_TTL : trendTtl;
        gapAnalysisTtl = gapAnalysisTtl == null ? DEFAULT_GAP_ANALYSIS_TTL : gapAnalysisTtl;
        projectInventoryTtl = projectInventoryTtl == null ? DEFAULT_PROJECT_INVENTORY_TTL : projectInventoryTtl;
    }
}
