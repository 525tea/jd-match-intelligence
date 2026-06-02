package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    @DisplayName("PENDING 상태이고 최대 재시도 횟수 미만인 이벤트를 생성 순서대로 조회한다")
    void findPendingEvents() {
        OutboxEvent firstPendingEvent = createEvent(1L, OutboxEventTypes.JOB_CREATED);
        OutboxEvent secondPendingEvent = createEvent(2L, OutboxEventTypes.JOB_UPDATED);
        OutboxEvent publishedEvent = createEvent(3L, OutboxEventTypes.JOB_CLOSED);
        OutboxEvent failedEvent = createEvent(4L, OutboxEventTypes.JOB_EXPIRED);
        OutboxEvent maxRetryEvent = createEvent(5L, OutboxEventTypes.APPLICATION_CREATED);

        publishedEvent.markPublished();

        failedEvent.markFailed("first failure", 3);
        failedEvent.markFailed("second failure", 3);
        failedEvent.markFailed("third failure", 3);

        maxRetryEvent.markFailed("first failure", 3);
        maxRetryEvent.markFailed("second failure", 3);
        maxRetryEvent.markFailed("third failure", 4);

        outboxEventRepository.save(firstPendingEvent);
        outboxEventRepository.save(publishedEvent);
        outboxEventRepository.save(secondPendingEvent);
        outboxEventRepository.save(failedEvent);
        outboxEventRepository.save(maxRetryEvent);
        outboxEventRepository.flush();

        List<OutboxEvent> events = outboxEventRepository
                .findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        3
                );

        assertThat(events)
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly(1L, 2L);
    }

    private OutboxEvent createEvent(Long aggregateId, String eventType) {
        return OutboxEvent.create(
                "JOB",
                aggregateId,
                eventType,
                """
                        {"id":1}
                        """,
                OutboxEvent.TOPIC_JOB_EVENTS
        );
    }
}
