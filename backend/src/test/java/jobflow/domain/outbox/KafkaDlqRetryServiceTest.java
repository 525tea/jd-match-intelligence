package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaDlqRetryServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private KafkaDlqRetryService kafkaDlqRetryService;

    @BeforeEach
    void setUp() {
        kafkaDlqRetryService = new KafkaDlqRetryService(kafkaTemplate, objectMapper, 3_000);
    }

    @Test
    @DisplayName("DLQ envelope의 original payload를 원본 topic으로 재발행한다")
    void retryDlqMessage() throws Exception {
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(successfulSend());
        DlqRetryRequest request = request("""
                {
                  "jobId": 1,
                  "title": "sample"
                }
                """);

        DlqRetryResponse response = kafkaDlqRetryService.retry(request);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("job.created"), eq("JOB:1"), payloadCaptor.capture());

        JsonNode retriedPayload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(retriedPayload.path("jobId").asLong()).isEqualTo(1L);
        assertThat(retriedPayload.path("title").asText()).isEqualTo("sample");
        assertThat(response.schemaVersion()).isEqualTo(1);
        assertThat(response.targetTopic()).isEqualTo("job.created");
        assertThat(response.targetKey()).isEqualTo("JOB:1");
    }

    @Test
    @DisplayName("문자열 payload는 원문 문자열로 재발행한다")
    void retryPlainTextPayload() throws Exception {
        given(kafkaTemplate.send(anyString(), any(), anyString()))
                .willReturn(successfulSend());
        DlqRetryRequest request = new DlqRetryRequest(
                1,
                "email.send",
                null,
                new DlqRetryRequest.OriginalPayload(objectMapper.getNodeFactory().textNode("plain-text"))
        );

        DlqRetryResponse response = kafkaDlqRetryService.retry(request);

        verify(kafkaTemplate).send("email.send", null, "plain-text");
        assertThat(response.targetTopic()).isEqualTo("email.send");
        assertThat(response.targetKey()).isNull();
    }

    @Test
    @DisplayName("지원하지 않는 schemaVersion이면 재발행하지 않는다")
    void rejectUnsupportedSchemaVersion() throws Exception {
        DlqRetryRequest request = new DlqRetryRequest(
                2,
                "job.created",
                "JOB:1",
                new DlqRetryRequest.OriginalPayload(objectMapper.readTree("{\"jobId\":1}"))
        );

        assertThatThrownBy(() -> kafkaDlqRetryService.retry(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE);
    }

    @Test
    @DisplayName("sourceTopic이 DLQ topic이면 재발행하지 않는다")
    void rejectDlqSourceTopic() throws Exception {
        DlqRetryRequest request = new DlqRetryRequest(
                1,
                "job.created.dlq",
                "JOB:1",
                new DlqRetryRequest.OriginalPayload(objectMapper.readTree("{\"jobId\":1}"))
        );

        assertThatThrownBy(() -> kafkaDlqRetryService.retry(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KAFKA_DLQ_INVALID_ENVELOPE);
    }

    @Test
    @DisplayName("Kafka 재발행 실패는 DLQ retry 실패로 노출한다")
    void failWhenKafkaPublishFails() throws Exception {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(failed);

        assertThatThrownBy(() -> kafkaDlqRetryService.retry(request("{\"jobId\":1}")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.KAFKA_DLQ_RETRY_FAILED);
    }

    private DlqRetryRequest request(String payload) throws Exception {
        return new DlqRetryRequest(
                1,
                "job.created",
                "JOB:1",
                new DlqRetryRequest.OriginalPayload(objectMapper.readTree(payload))
        );
    }

    private CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(null);
    }
}
