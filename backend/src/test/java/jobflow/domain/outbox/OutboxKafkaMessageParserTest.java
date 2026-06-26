package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OutboxKafkaMessageParserTest {

    private final OutboxKafkaMessageParser parser = new OutboxKafkaMessageParser(
            JsonMapper.builder().build()
    );

    @Test
    @DisplayName("Outbox Kafka envelope에서 payload와 metadata를 분리한다")
    void parseEnvelope() {
        String message = """
                {
                  "eventId": 10,
                  "aggregateType": "JOB",
                  "aggregateId": 20,
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 20,
                    "smokeRunId": "sample-run"
                  }
                }
                """;

        OutboxKafkaEnvelope envelope = parser.parseEnvelope(message);

        assertThat(envelope.eventId()).isEqualTo(10L);
        assertThat(envelope.aggregateType()).isEqualTo("JOB");
        assertThat(envelope.aggregateId()).isEqualTo(20L);
        assertThat(envelope.eventType()).isEqualTo("JOB_CREATED");
        assertThat(envelope.topic()).isEqualTo("job.created");
        assertThat(envelope.payload().path("jobId").asLong()).isEqualTo(20L);
        assertThat(envelope.payload().path("smokeRunId").asText()).isEqualTo("sample-run");
    }

    @Test
    @DisplayName("payload 필드가 없으면 root JSON을 payload로 사용한다")
    void parsePayloadOrRoot() {
        String message = """
                {
                  "to": "user@example.com",
                  "subject": "Sample subject",
                  "text": "Sample body"
                }
                """;

        assertThat(parser.parsePayloadOrRoot(message).path("to").asText())
                .isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("잘못된 JSON 메시지는 IllegalArgumentException으로 변환한다")
    void parseInvalidJson() {
        assertThatThrownBy(() -> parser.parseEnvelope("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka message is not valid JSON");
    }
}
