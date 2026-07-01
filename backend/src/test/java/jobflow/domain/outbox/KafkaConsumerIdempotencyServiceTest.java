package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaConsumerIdempotencyServiceTest {

    @Mock
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

    @Test
    @DisplayName("처리하지 않은 eventId면 side effect를 실행하고 처리 이력을 저장한다")
    void runOnce() {
        KafkaConsumerIdempotencyService service = new KafkaConsumerIdempotencyService(processedKafkaEventRepository);
        AtomicInteger sideEffectCount = new AtomicInteger();

        boolean handled = service.runOnce("email-send", 1L, sideEffectCount::incrementAndGet);

        assertThat(handled).isTrue();
        assertThat(sideEffectCount).hasValue(1);
        verify(processedKafkaEventRepository).saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedKafkaEvent.class));
    }

    @Test
    @DisplayName("이미 처리한 eventId면 side effect를 실행하지 않는다")
    void skipDuplicateEvent() {
        KafkaConsumerIdempotencyService service = new KafkaConsumerIdempotencyService(processedKafkaEventRepository);
        given(processedKafkaEventRepository.existsByConsumerNameAndEventId("email-send", 1L))
                .willReturn(true);
        AtomicInteger sideEffectCount = new AtomicInteger();

        boolean handled = service.runOnce("email-send", 1L, sideEffectCount::incrementAndGet);

        assertThat(handled).isFalse();
        assertThat(sideEffectCount).hasValue(0);
        verify(processedKafkaEventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedKafkaEvent.class));
    }

    @Test
    @DisplayName("eventId가 없는 legacy 메시지는 side effect를 실행한다")
    void runLegacyMessageWithoutEventId() {
        KafkaConsumerIdempotencyService service = new KafkaConsumerIdempotencyService(processedKafkaEventRepository);
        AtomicInteger sideEffectCount = new AtomicInteger();

        boolean handled = service.runOnce("email-send", null, sideEffectCount::incrementAndGet);

        assertThat(handled).isTrue();
        assertThat(sideEffectCount).hasValue(1);
        verify(processedKafkaEventRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(ProcessedKafkaEvent.class));
    }
}
