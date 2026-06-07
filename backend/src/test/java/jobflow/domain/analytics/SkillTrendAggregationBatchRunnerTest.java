package jobflow.domain.analytics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class SkillTrendAggregationBatchRunnerTest {

    @Test
    @DisplayName("target-month 설정이 있으면 해당 월 스킬 트렌드 Batch를 실행한다")
    void runWithConfiguredTargetMonth() throws Exception {
        SkillTrendAggregationBatchLauncher batchLauncher = mock(SkillTrendAggregationBatchLauncher.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        JobExecution jobExecution = mock(JobExecution.class);
        when(batchLauncher.launchMonthly(LocalDate.of(2026, 5, 1))).thenReturn(jobExecution);
        when(batchLauncher.jobName()).thenReturn("skillTrendAggregationJob");
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

        SkillTrendAggregationBatchRunner runner = new SkillTrendAggregationBatchRunner(batchLauncher, clock);
        ReflectionTestUtils.setField(runner, "targetMonth", "2026-05-01");

        runner.run(new DefaultApplicationArguments());

        verify(batchLauncher).launchMonthly(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("target-month 설정이 없으면 현재 월 스킬 트렌드 Batch를 실행한다")
    void runWithCurrentMonthWhenTargetMonthIsBlank() throws Exception {
        SkillTrendAggregationBatchLauncher batchLauncher = mock(SkillTrendAggregationBatchLauncher.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        JobExecution jobExecution = mock(JobExecution.class);
        when(batchLauncher.launchMonthly(LocalDate.of(2026, 6, 1))).thenReturn(jobExecution);
        when(batchLauncher.jobName()).thenReturn("skillTrendAggregationJob");
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getExitStatus()).thenReturn(ExitStatus.COMPLETED);

        SkillTrendAggregationBatchRunner runner = new SkillTrendAggregationBatchRunner(batchLauncher, clock);
        ReflectionTestUtils.setField(runner, "targetMonth", "");

        runner.run(new DefaultApplicationArguments());

        verify(batchLauncher).launchMonthly(LocalDate.of(2026, 6, 1));
    }
}
