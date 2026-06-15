package jobflow.global.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobFlowCachePropertiesTest {

    @Test
    @DisplayName("캐시 TTL 설정이 없으면 기본 TTL을 사용한다")
    void usesDefaultTtlWhenPropertiesAreNull() {
        JobFlowCacheProperties properties = new JobFlowCacheProperties(null, null, null, null);

        assertThat(properties.trendTtl()).isEqualTo(Duration.ofHours(6));
        assertThat(properties.gapAnalysisTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.jdMatchTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.projectInventoryTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("캐시 TTL 설정이 있으면 설정값을 사용한다")
    void usesConfiguredTtlWhenPropertiesAreProvided() {
        JobFlowCacheProperties properties = new JobFlowCacheProperties(
                Duration.ofHours(12),
                Duration.ofMinutes(10),
                Duration.ofMinutes(20),
                Duration.ofHours(2)
        );

        assertThat(properties.trendTtl()).isEqualTo(Duration.ofHours(12));
        assertThat(properties.gapAnalysisTtl()).isEqualTo(Duration.ofMinutes(10));
        assertThat(properties.jdMatchTtl()).isEqualTo(Duration.ofMinutes(20));
        assertThat(properties.projectInventoryTtl()).isEqualTo(Duration.ofHours(2));
    }
}
