package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
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
                  "schemaVersion": 1,
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

        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.eventId()).isEqualTo(10L);
        assertThat(envelope.aggregateType()).isEqualTo("JOB");
        assertThat(envelope.aggregateId()).isEqualTo(20L);
        assertThat(envelope.eventType()).isEqualTo("JOB_CREATED");
        assertThat(envelope.topic()).isEqualTo("job.created");
        assertThat(envelope.payload().path("jobId").asLong()).isEqualTo(20L);
        assertThat(envelope.payload().path("smokeRunId").asText()).isEqualTo("sample-run");
    }

    @Test
    @DisplayName("schemaVersion이 없으면 version 1 envelope로 해석한다")
    void parseEnvelopeWithoutSchemaVersion() {
        String message = """
                {
                  "eventId": 10,
                  "aggregateType": "JOB",
                  "aggregateId": 20,
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 20
                  }
                }
                """;

        OutboxKafkaEnvelope envelope = parser.parseEnvelope(message);

        assertThat(envelope.schemaVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("Debezium Outbox Event Router value envelope도 기존 consumer 계약으로 해석한다")
    void parseDebeziumOutboxEventRouterEnvelope() {
        String message = """
                {
                  "payload": {
                    "to": "user@example.com",
                    "subject": "Debezium outbox smoke sample-run",
                    "text": "Sample body",
                    "html": null,
                    "smokeRunId": "sample-run"
                  },
                  "schemaVersion": 1,
                  "aggregateType": "EMAIL",
                  "aggregateId": 30,
                  "eventType": "EMAIL_SEND_REQUESTED",
                  "topic": "email.send",
                  "eventId": 40
                }
                """;

        OutboxKafkaEnvelope envelope = parser.parseEnvelope(message);

        assertThat(envelope.schemaVersion()).isEqualTo(1);
        assertThat(envelope.eventId()).isEqualTo(40L);
        assertThat(envelope.aggregateType()).isEqualTo("EMAIL");
        assertThat(envelope.aggregateId()).isEqualTo(30L);
        assertThat(envelope.eventType()).isEqualTo("EMAIL_SEND_REQUESTED");
        assertThat(envelope.topic()).isEqualTo("email.send");
        assertThat(envelope.payload().path("to").asText()).isEqualTo("user@example.com");
        assertThat(envelope.payload().path("smokeRunId").asText()).isEqualTo("sample-run");
    }

    @Test
    @DisplayName("Debezium Event Router가 event id를 Kafka header에 둘 때도 envelope eventId로 해석한다")
    void parseDebeziumOutboxEventIdHeader() {
        String message = """
                {
                  "payload": {
                    "to": "user@example.com",
                    "subject": "Debezium outbox smoke sample-run",
                    "text": "Sample body",
                    "smokeRunId": "sample-run"
                  },
                  "schemaVersion": 1,
                  "aggregateType": "EMAIL",
                  "aggregateId": 30,
                  "eventType": "EMAIL_SEND_REQUESTED",
                  "topic": "email.send"
                }
                """;
        Headers headers = new RecordHeaders()
                .add("id", "40".getBytes(StandardCharsets.UTF_8));

        OutboxKafkaEnvelope envelope = parser.parseEnvelope(message, headers);

        assertThat(envelope.eventId()).isEqualTo(40L);
        assertThat(envelope.aggregateId()).isEqualTo(30L);
        assertThat(envelope.payload().path("smokeRunId").asText()).isEqualTo("sample-run");
    }

    @Test
    @DisplayName("value와 header에 eventId가 모두 있으면 value eventId를 우선한다")
    void preferValueEventIdOverDebeziumHeader() {
        String message = """
                {
                  "eventId": 50,
                  "payload": {
                    "to": "user@example.com",
                    "subject": "Sample subject",
                    "text": "Sample body"
                  }
                }
                """;
        Headers headers = new RecordHeaders()
                .add("id", "40".getBytes(StandardCharsets.UTF_8));

        OutboxKafkaEnvelope envelope = parser.parseEnvelope(message, headers);

        assertThat(envelope.eventId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Debezium event id header가 숫자가 아니면 실패한다")
    void failWhenDebeziumEventIdHeaderIsInvalid() {
        String message = """
                {
                  "payload": {
                    "to": "user@example.com",
                    "subject": "Sample subject",
                    "text": "Sample body"
                  }
                }
                """;
        Headers headers = new RecordHeaders()
                .add("id", "oops".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parseEnvelope(message, headers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka message header 'id' must be a long");
    }

    @Test
    @DisplayName("schemaVersion이 정수가 아니면 실패한다")
    void failWhenSchemaVersionIsInvalid() {
        assertThatThrownBy(() -> parser.parseEnvelope("""
                {
                  "schemaVersion": "v1",
                  "eventId": 10,
                  "aggregateType": "JOB",
                  "aggregateId": 20,
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 20
                  }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka message field 'schemaVersion' must be an integer");
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

    @Test
    @DisplayName("Envelope 숫자 필드가 숫자가 아니면 실패한다")
    void failWhenLongFieldIsInvalid() {
        assertThatThrownBy(() -> parser.parseEnvelope("""
                {
                  "eventId": "oops",
                  "aggregateType": "JOB",
                  "aggregateId": 20,
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 20
                  }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka message field 'eventId' must be a long");
    }

    @Test
    @DisplayName("Envelope 문자열 필드가 문자열이 아니면 실패한다")
    void failWhenTextFieldIsInvalid() {
        assertThatThrownBy(() -> parser.parseEnvelope("""
                {
                  "eventId": 10,
                  "aggregateType": ["JOB"],
                  "aggregateId": 20,
                  "eventType": "JOB_CREATED",
                  "topic": "job.created",
                  "payload": {
                    "jobId": 20
                  }
                }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Kafka message field 'aggregateType' must be a string");
    }
}
