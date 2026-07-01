package jobflow.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class KafkaDlqPublishingRecovererTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    private KafkaDlqPublishingRecoverer recoverer;

    @BeforeEach
    void setUp() {
        recoverer = new KafkaDlqPublishingRecoverer(
                kafkaTemplate,
                objectMapper,
                clock,
                ".dlq",
                3_000
        );
    }

    @Test
    @DisplayName("실패한 Kafka message를 metadata가 포함된 DLQ envelope로 발행한다")
    void publishDlqEnvelope() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "job.created",
                2,
                15L,
                "JOB:1",
                "{\"jobId\":1}"
        );
        given(kafkaTemplate.send(anyString(), nullable(String.class), anyString()))
                .willReturn(CompletableFuture.completedFuture(null));

        recoverer.accept(record, new IllegalStateException("index failed"));

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("job.created.dlq");
        assertThat(keyCaptor.getValue()).isEqualTo("JOB:1");

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertThat(message.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(message.path("sourceTopic").asText()).isEqualTo("job.created");
        assertThat(message.path("sourcePartition").asInt()).isEqualTo(2);
        assertThat(message.path("sourceOffset").asLong()).isEqualTo(15L);
        assertThat(message.path("sourceKey").asText()).isEqualTo("JOB:1");
        assertThat(message.path("failedAt").asText()).isEqualTo("2026-07-02T00:00Z");
        assertThat(message.path("error").path("className").asText())
                .isEqualTo(IllegalStateException.class.getName());
        assertThat(message.path("error").path("message").asText()).isEqualTo("index failed");
        assertThat(message.path("original").path("payload").path("jobId").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("원본 payload가 JSON이 아니면 문자열 payload로 보존한다")
    void publishDlqEnvelopeWithPlainTextPayload() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "email.send",
                0,
                3L,
                null,
                "plain-text"
        );
        given(kafkaTemplate.send(any(), any(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        recoverer.accept(record, new RuntimeException("boom"));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(any(), nullable(String.class), messageCaptor.capture());

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertThat(message.path("original").path("payload").asText()).isEqualTo("plain-text");
    }
}
