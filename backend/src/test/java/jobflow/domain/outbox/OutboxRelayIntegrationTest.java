package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        OutboxRelayService.class,
        OutboxRelayIntegrationTest.TestHandlerConfig.class
})
class OutboxRelayIntegrationTest {

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private RecordingOutboxEventHandler outboxEventHandler;

    @Test
    @DisplayName("저장된 PENDING outbox event를 handler로 처리하고 PUBLISHED 상태로 변경한다")
    void relayStoredPendingEvent() {
        OutboxEvent event = outboxEventRepository.save(OutboxEvent.create(
                "JOB",
                1L,
                OutboxEventTypes.JOB_CREATED,
                """
                        {"jobId":1}
                        """,
                OutboxEvent.TOPIC_JOB_EVENTS
        ));

        int relayedCount = outboxRelayService.relayPendingEvents();

        OutboxEvent relayedEvent = outboxEventRepository.findById(event.getId()).orElseThrow();

        assertThat(relayedCount).isEqualTo(1);
        assertThat(outboxEventHandler.handledEventIds()).containsExactly(event.getId());
        assertThat(relayedEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(relayedEvent.getPublishedAt()).isNotNull();
        assertThat(relayedEvent.getRetryCount()).isZero();
        assertThat(relayedEvent.getLastError()).isNull();
    }

    @TestConfiguration
    static class TestHandlerConfig {

        @Bean
        RecordingOutboxEventHandler recordingOutboxEventHandler() {
            return new RecordingOutboxEventHandler();
        }
    }

    static class RecordingOutboxEventHandler implements OutboxEventHandler {

        private final List<Long> handledEventIds = new ArrayList<>();

        @Override
        public boolean supports(OutboxEvent event) {
            return OutboxEventTypes.JOB_CREATED.equals(event.getEventType());
        }

        @Override
        public void handle(OutboxEvent event) {
            handledEventIds.add(event.getId());
        }

        List<Long> handledEventIds() {
            return handledEventIds;
        }
    }
}
