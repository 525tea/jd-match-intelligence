package jobflow.global.config;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "jobflow.kafka.consumer.enabled", havingValue = "true")
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> kafkaConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${jobflow.kafka.consumer.group-id:jobflow-backend}") String groupId,
            @Value("${jobflow.kafka.consumer.auto-offset-reset:latest}") String autoOffsetReset
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> kafkaConsumerFactory,
            DefaultErrorHandler kafkaDefaultErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setCommonErrorHandler(kafkaDefaultErrorHandler);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(
            KafkaDlqPublishingRecoverer kafkaDlqPublishingRecoverer,
            @Value("${jobflow.kafka.consumer.retry.backoff-interval-millis:1000}") long backoffIntervalMillis,
            @Value("${jobflow.kafka.consumer.retry.max-retries:3}") long maxRetries
    ) {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                kafkaDlqPublishingRecoverer,
                new FixedBackOff(backoffIntervalMillis, maxRetries)
        );
        errorHandler.setCommitRecovered(true);
        return errorHandler;
    }

    @Bean
    public KafkaDlqPublishingRecoverer kafkaDlqPublishingRecoverer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${jobflow.kafka.consumer.dlq.topic-suffix:.dlq}") String topicSuffix,
            @Value("${jobflow.kafka.consumer.dlq.send-timeout-millis:3000}") long sendTimeoutMillis
    ) {
        return new KafkaDlqPublishingRecoverer(
                kafkaTemplate,
                objectMapper,
                clock,
                topicSuffix,
                sendTimeoutMillis
        );
    }
}
