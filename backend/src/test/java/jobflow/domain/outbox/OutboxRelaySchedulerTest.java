package jobflow.domain.outbox;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelaySchedulerTest {

    @Mock
    private OutboxRelayService outboxRelayService;

    @Test
    @DisplayName("스케줄러 실행 시 relay service에 처리를 위임한다")
    void relayPendingEvents() {
        OutboxRelayScheduler scheduler = new OutboxRelayScheduler(outboxRelayService);

        scheduler.relayPendingEvents();

        verify(outboxRelayService).relayPendingEvents();
    }
}
