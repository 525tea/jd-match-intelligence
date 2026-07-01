package jobflow.global.cache;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
@EnableConfigurationProperties(JobFlowCacheProperties.class)
public class CacheConfig {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(30);

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            JobFlowCacheProperties cacheProperties
    ) {
        if (!cacheProperties.enabled()) {
            return new NoOpCacheManager();
        }

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(cacheConfiguration(DEFAULT_CACHE_TTL))
                .withInitialCacheConfigurations(cacheConfigurations(cacheProperties))
                .transactionAware()
                .enableStatistics()
                .build();
    }

    Map<String, RedisCacheConfiguration> cacheConfigurations(JobFlowCacheProperties cacheProperties) {
        return Map.ofEntries(
                Map.entry(CacheNames.JOB_SEARCH, cacheConfiguration(cacheProperties.jobSearchTtl())),
                Map.entry(CacheNames.TREND_SKILLS, cacheConfiguration(cacheProperties.trendTtl())),
                Map.entry(CacheNames.TREND_SKILL_COOCCURRENCES, cacheConfiguration(cacheProperties.trendTtl())),
                Map.entry(CacheNames.TREND_SKILL_EXPERIENCE_TAGS, cacheConfiguration(cacheProperties.trendTtl())),
                Map.entry(CacheNames.TREND_MARKET, cacheConfiguration(cacheProperties.trendTtl())),
                Map.entry(CacheNames.GAP_ANALYSIS, cacheConfiguration(cacheProperties.gapAnalysisTtl())),
                Map.entry(CacheNames.JD_MATCH, cacheConfiguration(cacheProperties.jdMatchTtl())),
                Map.entry(CacheNames.JOB_RECOMMENDATION, cacheConfiguration(cacheProperties.recommendationTtl())),
                Map.entry(CacheNames.PROJECT_SKILL_INVENTORY, cacheConfiguration(cacheProperties.projectInventoryTtl())),
                Map.entry(CacheNames.PROJECT_EXPERIENCE_TAG_INVENTORY, cacheConfiguration(cacheProperties.projectInventoryTtl()))
        );
    }

    private RedisCacheConfiguration cacheConfiguration(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(redisValueSerializer()));
    }

    RedisSerializer<Object> redisValueSerializer() {
        return new JdkSerializationRedisSerializer();
    }
}
