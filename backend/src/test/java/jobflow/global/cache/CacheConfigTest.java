package jobflow.global.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import jobflow.domain.analytics.dto.SkillTrendResponse;
import jobflow.domain.skill.SkillCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    private static JobFlowCacheProperties defaultProperties() {
        return new JobFlowCacheProperties(
                true,
                Duration.ofMinutes(5),
                Duration.ofHours(6),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                Duration.ofHours(1)
        );
    }

    @Test
    @DisplayName("Redis CacheManager를 생성한다")
    void createsRedisCacheManager() {
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);

        CacheManager cacheManager = cacheConfig.cacheManager(redisConnectionFactory, defaultProperties());

        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    @Test
    @DisplayName("캐시가 비활성화되면 NoOp CacheManager를 생성한다")
    void createsNoOpCacheManagerWhenDisabled() {
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
        JobFlowCacheProperties disabledProperties = new JobFlowCacheProperties(
                false,
                Duration.ofMinutes(5),
                Duration.ofHours(6),
                Duration.ofMinutes(30),
                Duration.ofMinutes(30),
                Duration.ofMinutes(10),
                Duration.ofHours(1)
        );

        CacheManager cacheManager = cacheConfig.cacheManager(redisConnectionFactory, disabledProperties);

        assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
    }

    @Test
    @DisplayName("JobFlow cache name별 Redis cache configuration을 등록한다")
    void registersJobFlowCacheConfigurations() {
        Map<String, RedisCacheConfiguration> cacheConfigurations = cacheConfig.cacheConfigurations(defaultProperties());

        assertThat(cacheConfigurations)
                .containsOnlyKeys(
                        CacheNames.JOB_SEARCH,
                        CacheNames.TREND_SKILLS,
                        CacheNames.TREND_SKILL_COOCCURRENCES,
                        CacheNames.TREND_SKILL_EXPERIENCE_TAGS,
                        CacheNames.TREND_MARKET,
                        CacheNames.GAP_ANALYSIS,
                        CacheNames.JD_MATCH,
                        CacheNames.JOB_RECOMMENDATION,
                        CacheNames.PROJECT_SKILL_INVENTORY,
                        CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY
                );
    }

    @Test
    @DisplayName("Redis cache value serializer는 List 응답을 다시 같은 타입으로 복원한다")
    void serializesAndDeserializesListResponse() {
        List<SkillTrendResponse> responses = List.of(
                new SkillTrendResponse(
                        1L,
                        "Java",
                        SkillCategory.LANGUAGE,
                        LocalDate.of(2026, 6, 1),
                        10,
                        8,
                        2,
                        BigDecimal.valueOf(18)
                )
        );

        byte[] serialized = cacheConfig.redisValueSerializer().serialize(responses);
        Object deserialized = cacheConfig.redisValueSerializer().deserialize(serialized);

        assertThat(deserialized).isEqualTo(responses);
    }
}
