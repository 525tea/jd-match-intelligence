package jobflow.global.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import tools.jackson.databind.ObjectMapper;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    @DisplayName("Redis CacheManager를 생성한다")
    void createsRedisCacheManager() {
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
        JobFlowCacheProperties cacheProperties = new JobFlowCacheProperties(
                Duration.ofHours(6),
                Duration.ofMinutes(30),
                Duration.ofHours(1)
        );
        ObjectMapper objectMapper = new ObjectMapper();

        CacheManager cacheManager = cacheConfig.cacheManager(
                redisConnectionFactory,
                cacheProperties,
                objectMapper
        );

        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    @DisplayName("JobFlow cache name별 Redis cache configuration을 등록한다")
    void registersJobFlowCacheConfigurations() {
        JobFlowCacheProperties cacheProperties = new JobFlowCacheProperties(
                Duration.ofHours(6),
                Duration.ofMinutes(30),
                Duration.ofHours(1)
        );
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, RedisCacheConfiguration> cacheConfigurations = cacheConfig.cacheConfigurations(
                cacheProperties,
                objectMapper
        );

        assertThat(cacheConfigurations)
                .containsOnlyKeys(
                        CacheNames.TREND_SKILLS,
                        CacheNames.TREND_SKILL_COOCCURRENCES,
                        CacheNames.TREND_SKILL_EXPERIENCE_TAGS,
                        CacheNames.TREND_MARKET,
                        CacheNames.GAP_ANALYSIS,
                        CacheNames.PROJECT_SKILL_INVENTORY,
                        CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY
                );
    }
}
