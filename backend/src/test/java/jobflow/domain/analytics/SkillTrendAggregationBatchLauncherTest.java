package jobflow.domain.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;

class SkillTrendAggregationBatchLauncherTest {

    @Test
    @DisplayName("월별 스킬 트렌드 Batch Job을 targetMonth parameter로 실행한다")
    void launchMonthlySkillTrendAggregationBatch() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job skillTrendAggregationJob = mock(Job.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        SkillTrendAggregationBatchLauncher launcher = new SkillTrendAggregationBatchLauncher(
                jobOperator,
                skillTrendAggregationJob,
                clock
        );

        launcher.launchMonthly(LocalDate.of(2026, 6, 7));

        verify(jobOperator).start(eq(skillTrendAggregationJob), org.mockito.ArgumentMatchers.argThat(parameters ->
                "2026-06-01".equals(parameters.getString("targetMonth"))
                        && parameters.getLong("requestedAt") == clock.millis()
        ));
    }

    @Test
    @DisplayName("Batch Job 이름을 반환한다")
    void jobName() {
        JobOperator jobOperator = mock(JobOperator.class);
        Job skillTrendAggregationJob = mock(Job.class);
        Clock clock = Clock.systemDefaultZone();
        when(skillTrendAggregationJob.getName()).thenReturn("skillTrendAggregationJob");
        SkillTrendAggregationBatchLauncher launcher = new SkillTrendAggregationBatchLauncher(
                jobOperator,
                skillTrendAggregationJob,
                clock
        );

        String jobName = launcher.jobName();

        assertThat(jobName).isEqualTo("skillTrendAggregationJob");
    }
}
