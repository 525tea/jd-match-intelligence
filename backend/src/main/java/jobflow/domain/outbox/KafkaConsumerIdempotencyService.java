package jobflow.domain.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerIdempotencyService {

    private final ProcessedKafkaEventRepository processedKafkaEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean runOnce(String consumerName, Long eventId, Runnable sideEffect) {
        if (eventId == null) {
            sideEffect.run();
            return true;
        }
        if (processedKafkaEventRepository.existsByConsumerNameAndEventId(consumerName, eventId)) {
            log.info("Kafka duplicate event skipped. consumerName={}, eventId={}", consumerName, eventId);
            return false;
        }

        try {
            processedKafkaEventRepository.saveAndFlush(ProcessedKafkaEvent.create(consumerName, eventId));
        } catch (DataIntegrityViolationException e) {
            log.info("Kafka duplicate event skipped by unique constraint. consumerName={}, eventId={}", consumerName, eventId);
            return false;
        }

        sideEffect.run();
        return true;
    }
}
