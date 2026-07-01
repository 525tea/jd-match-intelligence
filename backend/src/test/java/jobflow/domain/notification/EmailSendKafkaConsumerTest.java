package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jobflow.domain.outbox.KafkaConsumerIdempotencyService;
import jobflow.domain.outbox.OutboxKafkaMessageParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class EmailSendKafkaConsumerTest {

    private final OutboxKafkaMessageParser messageParser = new OutboxKafkaMessageParser(
            JsonMapper.builder().build()
    );

    @Mock
    private KafkaConsumerIdempotencyService idempotencyService;

    @Mock
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            Runnable sideEffect = invocation.getArgument(2);
            sideEffect.run();
            return true;
        }).when(idempotencyService).runOnce(any(), any(), any(Runnable.class));
    }

    @Test
    @DisplayName("email.send 메시지를 EmailSendRequest로 변환해 발송한다")
    void consumeEmailSendMessage() {
        EmailSendKafkaConsumer consumer = new EmailSendKafkaConsumer(messageParser, idempotencyService, emailSender);
        given(emailSender.send(org.mockito.ArgumentMatchers.any()))
                .willReturn(EmailSendResult.sent("MOCK_EMAIL", "sample-message-id"));

        consumer.consume("""
                {
                  "to": "user@example.com",
                  "subject": "Sample subject",
                  "text": "Sample body",
                  "html": "<p>Sample body</p>",
                  "smokeRunId": "sample-consumer-smoke"
                }
                """);

        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailSender).send(requestCaptor.capture());

        EmailSendRequest request = requestCaptor.getValue();
        assertThat(request.to()).isEqualTo("user@example.com");
        assertThat(request.subject()).isEqualTo("Sample subject");
        assertThat(request.text()).isEqualTo("Sample body");
        assertThat(request.html()).isEqualTo("<p>Sample body</p>");
    }

    @Test
    @DisplayName("Outbox envelope의 payload를 email.send 요청으로 사용한다")
    void consumeEmailSendEnvelope() {
        EmailSendKafkaConsumer consumer = new EmailSendKafkaConsumer(messageParser, idempotencyService, emailSender);
        given(emailSender.send(org.mockito.ArgumentMatchers.any()))
                .willReturn(EmailSendResult.sent("MOCK_EMAIL", "sample-message-id"));

        consumer.consume("""
                {
                  "eventId": 10,
                  "topic": "email.send",
                  "payload": {
                    "to": "user@example.com",
                    "subject": "Envelope subject",
                    "text": "Envelope body"
                  }
                }
                """);

        ArgumentCaptor<EmailSendRequest> requestCaptor = ArgumentCaptor.forClass(EmailSendRequest.class);
        verify(emailSender).send(requestCaptor.capture());
        assertThat(requestCaptor.getValue().subject()).isEqualTo("Envelope subject");
    }

    @Test
    @DisplayName("이메일 발송 실패 결과는 예외로 전파해 consumer retry 대상으로 남긴다")
    void failWhenEmailSenderFails() {
        EmailSendKafkaConsumer consumer = new EmailSendKafkaConsumer(messageParser, idempotencyService, emailSender);
        given(emailSender.send(org.mockito.ArgumentMatchers.any()))
                .willReturn(EmailSendResult.failed("MOCK_EMAIL", "forced failure"));

        assertThatThrownBy(() -> consumer.consume("""
                {
                  "to": "user@example.com",
                  "subject": "Sample subject",
                  "text": "Sample body"
                }
                """))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka email send failed. provider=MOCK_EMAIL, reason=forced failure");
    }

    @Test
    @DisplayName("중복 이벤트는 이메일 발송 side effect를 실행하지 않는다")
    void skipDuplicateEmailSendEvent() {
        EmailSendKafkaConsumer consumer = new EmailSendKafkaConsumer(messageParser, idempotencyService, emailSender);
        given(idempotencyService.runOnce(any(), any(), any(Runnable.class))).willReturn(false);

        consumer.consume("""
                {
                  "eventId": 10,
                  "topic": "email.send",
                  "payload": {
                    "to": "user@example.com",
                    "subject": "Envelope subject",
                    "text": "Envelope body"
                  }
                }
                """);

        verify(emailSender, never()).send(any());
    }
}
