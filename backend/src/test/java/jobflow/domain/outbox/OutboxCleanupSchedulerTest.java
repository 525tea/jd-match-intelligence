package jobflow.domain.outbox;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxCleanupService, true);

        scheduler.cleanupProcessedEvents();

        verify(outboxCleanupService).cleanupProcessedEvents();
    }

    @Test
    @DisplayName("스케줄러가 비활성화되어 있으면 cleanup service를 호출하지 않는다")
    void skipCleanupWhenDisabled() {
        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxCleanupService, false);

        scheduler.cleanupProcessedEvents();

        verifyNoInteractions(outboxCleanupService);
    }

    @Test
    @DisplayName("cleanup service 예외가 발생해도 스케줄러 실행을 삼킨다")
    void containCleanupFailure() {
        OutboxCleanupScheduler scheduler = new OutboxCleanupScheduler(outboxCleanupService, true);
        given(outboxCleanupService.cleanupProcessedEvents()).willThrow(new IllegalStateException("db unavailable"));

        scheduler.cleanupProcessedEvents();

        verify(outboxCleanupService).cleanupProcessedEvents();
    }
}
