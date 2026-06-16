package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockEmailSenderTest {

    @Test
    @DisplayName("Mock email sender는 외부 호출 없이 성공 결과와 provider message id를 반환한다")
    void sendMockEmail() {
        MockEmailSender sender = new MockEmailSender(
                new MockEmailSenderProperties(false, null, "test-message")
        );

        EmailSendResult result = sender.send(new EmailSendRequest(
                "user@example.com",
                "Deadline reminder",
                "Your saved job closes soon.",
                null
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(MockEmailSender.PROVIDER);
        assertThat(result.providerMessageId()).isEqualTo("test-message-1");
        assertThat(result.failureReason()).isNull();
    }

    @Test
    @DisplayName("Mock email sender는 실패 모드에서 실패 결과를 반환한다")
    void failMockEmail() {
        MockEmailSender sender = new MockEmailSender(
                new MockEmailSenderProperties(true, "test failure", "test-message")
        );

        EmailSendResult result = sender.send(new EmailSendRequest(
                "user@example.com",
                "Deadline reminder",
                "Your saved job closes soon.",
                null
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(MockEmailSender.PROVIDER);
        assertThat(result.providerMessageId()).isNull();
        assertThat(result.failureReason()).isEqualTo("test failure");
    }

    @Test
    @DisplayName("Mock email sender는 잘못된 요청을 실패 결과로 변환한다")
    void rejectInvalidRequest() {
        MockEmailSender sender = new MockEmailSender(
                new MockEmailSenderProperties(false, null, null)
        );

        EmailSendResult result = sender.send(new EmailSendRequest(
                "",
                "Deadline reminder",
                "Your saved job closes soon.",
                null
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(MockEmailSender.PROVIDER);
        assertThat(result.failureReason()).isEqualTo("Email request is invalid");
    }
}
