package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<OutboxEvent> outboxEventCaptor;

    @InjectMocks
    private OutboxEventService outboxEventService;

    @Test
    @DisplayName("payload를 JSON으로 직렬화해 outbox event를 저장한다")
    void save() throws Exception {
        TestPayload payload = new TestPayload(1L, "백엔드 개발자");

        given(objectMapper.writeValueAsString(payload))
                .willReturn("""
                        {"jobId":1,"title":"백엔드 개발자"}
                        """);

        outboxEventService.save(
                "JOB",
                1L,
                OutboxEventTypes.JOB_CREATED,
                payload,
                OutboxEvent.TOPIC_JOB_EVENTS
        );

        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OutboxEvent event = outboxEventCaptor.getValue();

        assertThat(event.getAggregateType()).isEqualTo("JOB");
        assertThat(event.getAggregateId()).isEqualTo(1L);
        assertThat(event.getEventType()).isEqualTo(OutboxEventTypes.JOB_CREATED);
        assertThat(event.getPayload()).contains("\"jobId\":1");
        assertThat(event.getPayload()).contains("\"title\":\"백엔드 개발자\"");
        assertThat(event.getTopic()).isEqualTo(OutboxEvent.TOPIC_JOB_EVENTS);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isZero();
    }

    @Test
    @DisplayName("payload 직렬화 실패 시 내부 서버 오류 예외를 던진다")
    void saveWithSerializationFail() throws Exception {
        TestPayload payload = new TestPayload(1L, "백엔드 개발자");

        given(objectMapper.writeValueAsString(payload))
                .willThrow(new TestJacksonException("serialization failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> outboxEventService.save(
                        "JOB",
                        1L,
                        OutboxEventTypes.JOB_CREATED,
                        payload,
                        OutboxEvent.TOPIC_JOB_EVENTS
                ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMON_INTERNAL_ERROR);
    }

    private record TestPayload(
            Long jobId,
            String title
    ) {
    }

    private static class TestJacksonException extends JacksonException {

        TestJacksonException(String message) {
            super(message);
        }
    }
}
