package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.Optional;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class DlqMessageServiceTest {

    @Mock
    private DlqMessageRepository dlqMessageRepository;

    @Mock
    private KafkaDlqRetryService kafkaDlqRetryService;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    @DisplayName("DLQ envelope metadata를 DB 저장용 메시지로 변환해 저장한다")
    void saveIfAbsent() {
        DlqMessageService service = new DlqMessageService(dlqMessageRepository, kafkaDlqRetryService, objectMapper);

        service.saveIfAbsent(envelope());

        ArgumentCaptor<DlqMessage> messageCaptor = ArgumentCaptor.forClass(DlqMessage.class);
        verify(dlqMessageRepository).save(messageCaptor.capture());
        DlqMessage message = messageCaptor.getValue();
        assertThat(message.getSchemaVersion()).isEqualTo(1);
        assertThat(message.getSourceTopic()).isEqualTo("job.created");
        assertThat(message.getSourcePartition()).isEqualTo(2);
        assertThat(message.getSourceOffset()).isEqualTo(15L);
        assertThat(message.getSourceKey()).isEqualTo("JOB:1");
        assertThat(message.getFailedAt()).isEqualTo(OffsetDateTime.parse("2026-07-02T00:00Z").toLocalDateTime());
    }

    @Test
    @DisplayName("저장된 DLQ 메시지를 원본 topic으로 재발행하고 RETRIED 상태로 바꾼다")
    void retryById() {
        DlqMessageService service = new DlqMessageService(dlqMessageRepository, kafkaDlqRetryService, objectMapper);
        DlqMessage message = DlqMessage.create(
                1,
                "job.created",
                2,
                15L,
                "JOB:1",
                envelope(),
                OffsetDateTime.parse("2026-07-02T00:00Z")
        );
        given(dlqMessageRepository.findById(1L)).willReturn(Optional.of(message));
        given(kafkaDlqRetryService.retry(org.mockito.ArgumentMatchers.any(DlqRetryRequest.class)))
                .willReturn(new DlqRetryResponse(1, "job.created", "JOB:1"));

        DlqRetryResponse response = service.retry(1L);

        assertThat(response.targetTopic()).isEqualTo("job.created");
        assertThat(message.getStatus()).isEqualTo(DlqMessageStatus.RETRIED);
        assertThat(message.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 재처리된 DLQ 메시지는 다시 재처리하지 않는다")
    void rejectAlreadyRetriedMessage() {
        DlqMessageService service = new DlqMessageService(dlqMessageRepository, kafkaDlqRetryService, objectMapper);
        DlqMessage message = DlqMessage.create(
                1,
                "job.created",
                2,
                15L,
                "JOB:1",
                envelope(),
                OffsetDateTime.parse("2026-07-02T00:00Z")
        );
        message.markRetried();
        given(dlqMessageRepository.findById(1L)).willReturn(Optional.of(message));

        assertThatThrownBy(() -> service.retry(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KAFKA_DLQ_MESSAGE_STATUS_CONFLICT);
    }

    private String envelope() {
        return """
                {
                  "schemaVersion": 1,
                  "sourceTopic": "job.created",
                  "sourcePartition": 2,
                  "sourceOffset": 15,
                  "sourceKey": "JOB:1",
                  "failedAt": "2026-07-02T00:00Z",
                  "original": {
                    "payload": {
                      "jobId": 1
                    }
                  }
                }
                """;
    }
}
