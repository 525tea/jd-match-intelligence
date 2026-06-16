package jobflow.domain.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeadlineReminderSchedulerTest {

    @Test
    @DisplayName("scheduler는 신규 마감 알림과 재시도 알림을 순서대로 실행한다")
    void sendDeadlineReminders() {
        DeadlineReminderBatchService batchService = mock(DeadlineReminderBatchService.class);
        when(batchService.sendDueSoonReminders()).thenReturn(new DeadlineReminderBatchResult(1, 1, 0, 0));
        when(batchService.retryPendingReminders()).thenReturn(new DeadlineReminderBatchResult(1, 1, 0, 0));
        DeadlineReminderScheduler scheduler = new DeadlineReminderScheduler(batchService);

        scheduler.sendDeadlineReminders();

        verify(batchService).sendDueSoonReminders();
        verify(batchService).retryPendingReminders();
    }
}
