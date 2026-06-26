package jobflow.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaGatewaySecurityEventPublisher implements GatewaySecurityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaGatewaySecurityEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String topic;

    public KafkaGatewaySecurityEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${gateway.security-events.enabled:false}") boolean enabled,
            @Value("${gateway.security-events.topic:security.events}") String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.topic = topic;
    }

    @Override
    public void publish(GatewaySecurityEvent event) {
        if (!enabled) {
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(event);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.requestId(), payload);
            kafkaTemplate.send(record)
                    .whenComplete((result, throwable) -> {
                        if (throwable != null) {
                            log.warn(
                                    "Gateway security event publish failed. eventType={}, requestId={}",
                                    event.eventType(),
                                    event.requestId(),
                                    throwable
                            );
                        }
                    });
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Gateway security event serialization failed. eventType={}, requestId={}",
                    event.eventType(),
                    event.requestId(),
                    exception
            );
        }
    }
}
