package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadlineReminderPropertiesTest {

    @Test
    @DisplayName("deadline reminder property 기본값을 제공한다")
    void defaults() {
        DeadlineReminderProperties properties = new DeadlineReminderProperties(null, null, 0);

        assertThat(properties.window()).isEqualTo(Duration.ofHours(24));
        assertThat(properties.idempotencyTtl()).isEqualTo(Duration.ofHours(25));
        assertThat(properties.maxAttempts()).isEqualTo(3);
    }

    @Test
    @DisplayName("명시한 deadline reminder property 값을 사용한다")
    void explicitValues() {
        DeadlineReminderProperties properties = new DeadlineReminderProperties(
                Duration.ofHours(12),
                Duration.ofHours(13),
                5
        );

        assertThat(properties.window()).isEqualTo(Duration.ofHours(12));
        assertThat(properties.idempotencyTtl()).isEqualTo(Duration.ofHours(13));
        assertThat(properties.maxAttempts()).isEqualTo(5);
    }
}
