package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedKafkaEventRepository processedKafkaEventRepository;

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

    @Test
    @DisplayName("relay batch는 설정된 size만큼 생성 순서대로 조회한다")
    void findRelayBatch() {
        outboxEventRepository.save(createEvent(1L, OutboxEventTypes.JOB_CREATED));
        outboxEventRepository.save(createEvent(2L, OutboxEventTypes.JOB_UPDATED));
        outboxEventRepository.save(createEvent(3L, OutboxEventTypes.JOB_CLOSED));
        outboxEventRepository.flush();

        List<OutboxEvent> events = outboxEventRepository.findRelayBatch(
                OutboxStatus.PENDING,
                3,
                PageRequest.of(0, 2)
        );

        assertThat(events)
                .extracting(OutboxEvent::getAggregateId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("cleanup 후보는 PUBLISHED 이벤트와 consumer 처리 완료된 PENDING 이벤트만 조회한다")
    void findCleanupCandidateIds() {
        OutboxEvent processedPendingEvent = outboxEventRepository.save(createEvent(1L, OutboxEventTypes.JOB_CREATED));
        OutboxEvent unprocessedPendingEvent = outboxEventRepository.save(createEvent(2L, OutboxEventTypes.JOB_UPDATED));
        OutboxEvent publishedEvent = createEvent(3L, OutboxEventTypes.JOB_CLOSED);
        publishedEvent.markPublished();
        outboxEventRepository.save(publishedEvent);
        OutboxEvent failedEvent = createEvent(4L, OutboxEventTypes.JOB_EXPIRED);
        failedEvent.markFailed("first failure", 1);
        outboxEventRepository.save(failedEvent);
        outboxEventRepository.flush();
        processedKafkaEventRepository.save(ProcessedKafkaEvent.create("job-search-index", processedPendingEvent.getId()));
        processedKafkaEventRepository.flush();

        List<Long> candidateIds = outboxEventRepository.findCleanupCandidateIds(
                processedPendingEvent.getCreatedAt().plusSeconds(1),
                PageRequest.of(0, 10)
        );

        assertThat(candidateIds)
                .containsExactlyInAnyOrder(processedPendingEvent.getId(), publishedEvent.getId());
        assertThat(candidateIds)
                .doesNotContain(unprocessedPendingEvent.getId(), failedEvent.getId());
    }

    @Test
    @DisplayName("cleanup 후보 id 목록을 bulk delete한다")
    void deleteByIdIn() {
        OutboxEvent firstEvent = outboxEventRepository.save(createEvent(1L, OutboxEventTypes.JOB_CREATED));
        OutboxEvent secondEvent = outboxEventRepository.save(createEvent(2L, OutboxEventTypes.JOB_UPDATED));
        OutboxEvent remainingEvent = outboxEventRepository.save(createEvent(3L, OutboxEventTypes.JOB_CLOSED));
        outboxEventRepository.flush();

        int deletedCount = outboxEventRepository.deleteByIdIn(List.of(firstEvent.getId(), secondEvent.getId()));
        outboxEventRepository.flush();

        assertThat(deletedCount).isEqualTo(2);
        assertThat(outboxEventRepository.findAll())
                .extracting(OutboxEvent::getId)
                .containsExactly(remainingEvent.getId());
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
