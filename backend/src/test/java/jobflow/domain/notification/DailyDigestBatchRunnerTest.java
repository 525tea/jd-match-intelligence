package jobflow.domain.notification;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class DailyDigestBatchRunnerTest {

    @Test
    @DisplayName("runner 기본 mode는 Daily Digest 발송을 실행한다")
    void runDailyMode() {
        DailyDigestBatchService batchService = mock(DailyDigestBatchService.class);
        when(batchService.sendDailyDigests(List.of(JobRole.BACKEND, JobRole.FULLSTACK), CareerLevel.MID))
                .thenReturn(new DailyDigestBatchResult(1, 1, 0, 0));
        DailyDigestBatchRunner runner = new DailyDigestBatchRunner(batchService);
        ReflectionTestUtils.setField(runner, "mode", "daily");
        ReflectionTestUtils.setField(runner, "targetRoles", "BACKEND,FULLSTACK");
        ReflectionTestUtils.setField(runner, "targetCareerLevel", "MID");

        runner.run(new DefaultApplicationArguments());

        verify(batchService).sendDailyDigests(List.of(JobRole.BACKEND, JobRole.FULLSTACK), CareerLevel.MID);
        verify(batchService, never()).retryPendingDailyDigests(
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                CareerLevel.MID
        );
    }

    @Test
    @DisplayName("runner retry mode는 PENDING Daily Digest 재시도를 실행한다")
    void runRetryMode() {
        DailyDigestBatchService batchService = mock(DailyDigestBatchService.class);
        when(batchService.retryPendingDailyDigests(List.of(JobRole.BACKEND), null))
                .thenReturn(new DailyDigestBatchResult(1, 1, 0, 0));
        DailyDigestBatchRunner runner = new DailyDigestBatchRunner(batchService);
        ReflectionTestUtils.setField(runner, "mode", "retry");
        ReflectionTestUtils.setField(runner, "targetRoles", "BACKEND");
        ReflectionTestUtils.setField(runner, "targetCareerLevel", "");

        runner.run(new DefaultApplicationArguments());

        verify(batchService).retryPendingDailyDigests(List.of(JobRole.BACKEND), null);
        verify(batchService, never()).sendDailyDigests(List.of(JobRole.BACKEND), null);
    }

    @Test
    @DisplayName("target role과 career level이 비어 있으면 filter 없이 실행한다")
    void runWithoutFilters() {
        DailyDigestBatchService batchService = mock(DailyDigestBatchService.class);
        when(batchService.sendDailyDigests(List.of(), null))
                .thenReturn(new DailyDigestBatchResult(1, 1, 0, 0));
        DailyDigestBatchRunner runner = new DailyDigestBatchRunner(batchService);
        ReflectionTestUtils.setField(runner, "mode", "daily");
        ReflectionTestUtils.setField(runner, "targetRoles", "");
        ReflectionTestUtils.setField(runner, "targetCareerLevel", "");

        runner.run(new DefaultApplicationArguments());

        verify(batchService).sendDailyDigests(List.of(), null);
        verify(batchService, never()).retryPendingDailyDigests(List.of(), null);
    }
}
