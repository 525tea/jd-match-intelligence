package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailgunPropertiesTest {

    @Test
    @DisplayName("Mailgun 기본 설정값을 보정한다")
    void defaults() {
        MailgunProperties properties = new MailgunProperties(null, null, null, null);

        assertThat(properties.baseUrl()).isEqualTo("https://api.mailgun.net");
        assertThat(properties.from()).isEqualTo("no-reply@localhost");
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("Mailgun baseUrl 끝의 slash를 제거하고 필수 설정 여부를 판단한다")
    void configured() {
        MailgunProperties properties = new MailgunProperties(
                "https://api.mailgun.test/",
                "mail.example.test",
                "test-mailgun-api-key",
                "JobFlow <no-reply@example.test>"
        );

        assertThat(properties.baseUrl()).isEqualTo("https://api.mailgun.test");
        assertThat(properties.isConfigured()).isTrue();
    }
}
