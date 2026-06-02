package jobflow.domain.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventHandler outboxEventHandler;

    private OutboxRelayService outboxRelayService;

    @BeforeEach
    void setUp() {
        outboxRelayService = new OutboxRelayService(
                outboxEventRepository,
                List.of(outboxEventHandler)
        );
    }

    @Test
    @DisplayName("PENDING 이벤트를 조회해 handler 처리 후 PUBLISHED 상태로 변경한다")
    void relayPendingEvents() {
        OutboxEvent event = createEvent();

        given(outboxEventRepository.findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                OutboxRelayService.MAX_RETRY_COUNT
        )).willReturn(List.of(event));
        given(outboxEventHandler.supports(event)).willReturn(true);

        int relayedCount = outboxRelayService.relayPendingEvents();

        assertThat(relayedCount).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();

        verify(outboxEventHandler).handle(event);
    }

    @Test
    @DisplayName("handler 처리 실패 시 retryCount와 lastError를 기록한다")
    void relayPendingEventsWithHandlerFailure() {
        OutboxEvent event = createEvent();

        given(outboxEventRepository.findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                OutboxRelayService.MAX_RETRY_COUNT
        )).willReturn(List.of(event));
        given(outboxEventHandler.supports(event)).willReturn(true);
        willThrow(new IllegalStateException("handler failed"))
                .given(outboxEventHandler)
                .handle(event);

        int relayedCount = outboxRelayService.relayPendingEvents();

        assertThat(relayedCount).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("handler failed");
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("최대 재시도 횟수에 도달한 실패 이벤트는 FAILED 상태로 변경한다")
    void relayPendingEventsWithMaxRetryFailure() {
        OutboxEvent event = createEvent();
        event.markFailed("first failure", OutboxRelayService.MAX_RETRY_COUNT);
        event.markFailed("second failure", OutboxRelayService.MAX_RETRY_COUNT);

        given(outboxEventRepository.findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                OutboxRelayService.MAX_RETRY_COUNT
        )).willReturn(List.of(event));
        given(outboxEventHandler.supports(event)).willReturn(true);
        willThrow(new IllegalStateException("third failure"))
                .given(outboxEventHandler)
                .handle(event);

        outboxRelayService.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getLastError()).isEqualTo("third failure");
    }

    @Test
    @DisplayName("지원하는 handler가 없으면 실패로 기록한다")
    void relayPendingEventsWithoutSupportedHandler() {
        OutboxEvent event = createEvent();

        given(outboxEventRepository.findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                OutboxRelayService.MAX_RETRY_COUNT
        )).willReturn(List.of(event));
        given(outboxEventHandler.supports(event)).willReturn(false);

        outboxRelayService.relayPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getLastError())
                .contains("No outbox event handler found");
    }

    @Test
    @DisplayName("처리할 PENDING 이벤트가 없으면 0을 반환한다")
    void relayPendingEventsWithEmptyEvents() {
        given(outboxEventRepository.findTop100ByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxStatus.PENDING,
                OutboxRelayService.MAX_RETRY_COUNT
        )).willReturn(List.of());

        int relayedCount = outboxRelayService.relayPendingEvents();

        assertThat(relayedCount).isZero();
    }

    private OutboxEvent createEvent() {
        return OutboxEvent.create(
                "JOB",
                1L,
                OutboxEventTypes.JOB_CREATED,
                """
                        {"jobId":1}
                        """,
                OutboxEvent.TOPIC_JOB_EVENTS
        );
    }
}
