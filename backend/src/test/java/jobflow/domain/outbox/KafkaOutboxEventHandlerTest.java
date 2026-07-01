package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
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
class KafkaOutboxEventHandlerTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private KafkaOutboxEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new KafkaOutboxEventHandler(kafkaTemplate, objectMapper, 3_000);
    }

    @Test
    @DisplayName("Outbox event를 Kafka envelope로 발행한다")
    void publishOutboxEvent() throws Exception {
        OutboxEvent event = OutboxEvent.create(
                "JOB",
                1L,
                OutboxEventTypes.JOB_CREATED,
                "{\"jobId\":1,\"title\":\"sample\"}",
                OutboxEvent.TOPIC_JOB_EVENTS
        );
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(successfulSend());

        handler.handle(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), messageCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(OutboxEvent.TOPIC_JOB_EVENTS);
        assertThat(keyCaptor.getValue()).isEqualTo("JOB:1");

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertThat(message.path("eventId").isNull()).isTrue();
        assertThat(message.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(message.path("aggregateType").asText()).isEqualTo("JOB");
        assertThat(message.path("aggregateId").asLong()).isEqualTo(1L);
        assertThat(message.path("eventType").asText()).isEqualTo(OutboxEventTypes.JOB_CREATED);
        assertThat(message.path("topic").asText()).isEqualTo(OutboxEvent.TOPIC_JOB_EVENTS);
        assertThat(message.path("payload").path("title").asText()).isEqualTo("sample");
    }

    @Test
    @DisplayName("legacy job.events topic은 job.created로 발행한다")
    void publishLegacyJobEventsTopicToJobCreated() throws Exception {
        OutboxEvent event = OutboxEvent.create(
                "JOB",
                2L,
                OutboxEventTypes.JOB_CREATED,
                "{\"jobId\":2}",
                "job.events"
        );
        given(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .willReturn(successfulSend());

        handler.handle(event);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(OutboxEvent.TOPIC_JOB_EVENTS), eq("JOB:2"), messageCaptor.capture());

        JsonNode message = objectMapper.readTree(messageCaptor.getValue());
        assertThat(message.path("topic").asText()).isEqualTo(OutboxEvent.TOPIC_JOB_EVENTS);
        assertThat(message.path("payload").path("jobId").asLong()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Kafka 발행 실패 시 relay 재시도를 위해 예외를 던진다")
    void throwWhenKafkaSendFails() {
        OutboxEvent event = OutboxEvent.create(
                "JOB",
                1L,
                OutboxEventTypes.JOB_CREATED,
                "{\"jobId\":1}",
                OutboxEvent.TOPIC_JOB_EVENTS
        );
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("kafka down"));
        given(kafkaTemplate.send(anyString(), anyString(), anyString())).willReturn(failed);

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish outbox event to Kafka");
    }

    private CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(null);
    }
}
