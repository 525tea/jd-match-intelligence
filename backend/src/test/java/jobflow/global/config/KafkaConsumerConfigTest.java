package jobflow.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigTest {

    @Mock
    private ConsumerFactory<String, String> consumerFactory;

    @Mock
    private DefaultErrorHandler errorHandler;

    @Test
    @DisplayName("Kafka listener container concurrency를 설정값으로 적용한다")
    void setListenerConcurrency() throws Exception {
        KafkaConsumerConfig config = new KafkaConsumerConfig();

        var factory = config.kafkaListenerContainerFactory(consumerFactory, errorHandler, 3);

        var concurrencyField = factory.getClass().getDeclaredField("concurrency");
        concurrencyField.setAccessible(true);
        assertThat(concurrencyField.get(factory)).isEqualTo(3);
    }
}
