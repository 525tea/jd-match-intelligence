package jobflow.global.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;

class RedisTimeoutPropertiesTest {

    @Test
    @DisplayName("Redis read/connect timeout property를 Spring Boot Redis 설정에 바인딩한다")
    void bindsRedisTimeoutProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource();
        source.put("spring.data.redis.connect-timeout", "500ms");
        source.put("spring.data.redis.timeout", "750ms");

        DataRedisProperties properties = new Binder(source)
                .bind("spring.data.redis", DataRedisProperties.class)
                .orElseThrow(() -> new AssertionError("spring.data.redis properties were not bound"));

        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(500));
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofMillis(750));
    }
}
