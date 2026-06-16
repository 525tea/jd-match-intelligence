package jobflow.domain.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class DeadlineReminderBatchRunnerTest {

    @Test
    @DisplayName("runner 기본 mode는 마감 임박 알림 발송을 실행한다")
    void runDueSoonMode() {
        DeadlineReminderBatchService batchService = mock(DeadlineReminderBatchService.class);
        when(batchService.sendDueSoonReminders()).thenReturn(new DeadlineReminderBatchResult(1, 1, 0, 0));
        DeadlineReminderBatchRunner runner = new DeadlineReminderBatchRunner(batchService);
        ReflectionTestUtils.setField(runner, "mode", "due-soon");

        runner.run(new DefaultApplicationArguments());

        verify(batchService).sendDueSoonReminders();
        verify(batchService, never()).retryPendingReminders();
    }

    @Test
    @DisplayName("runner retry mode는 PENDING 알림 재시도를 실행한다")
    void runRetryMode() {
        DeadlineReminderBatchService batchService = mock(DeadlineReminderBatchService.class);
        when(batchService.retryPendingReminders()).thenReturn(new DeadlineReminderBatchResult(1, 1, 0, 0));
        DeadlineReminderBatchRunner runner = new DeadlineReminderBatchRunner(batchService);
        ReflectionTestUtils.setField(runner, "mode", "retry");

        runner.run(new DefaultApplicationArguments());

        verify(batchService).retryPendingReminders();
        verify(batchService, never()).sendDueSoonReminders();
    }
}
