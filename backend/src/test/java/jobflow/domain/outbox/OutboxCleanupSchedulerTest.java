package jobflow.domain.outbox;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupSchedulerTest {

    @Mock
    private OutboxCleanupService outboxCleanupService;

    @Test
    @DisplayName("스케줄러 실행 시 cleanup service에 처리를 위임한다")
    void cleanupProcessedEvents() {
        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxCleanupService);

        scheduler.cleanupProcessedEvents();

        verify(outboxCleanupService).cleanupProcessedEvents();
    }
}
