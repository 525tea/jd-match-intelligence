package jobflow.domain.analytics;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;

class SkillTrendAggregationSchedulerTest {

    @Test
    @DisplayName("스케줄러는 현재 월 스킬 트렌드 Batch Job을 실행한다")
    void aggregateCurrentMonthSkillTrends() throws Exception {
        JobOperator jobOperator = mock(JobOperator.class);
        Job skillTrendAggregationJob = mock(Job.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        SkillTrendAggregationScheduler scheduler = new SkillTrendAggregationScheduler(
                jobOperator,
                skillTrendAggregationJob,
                clock
        );

        scheduler.aggregateCurrentMonthSkillTrends();

        verify(jobOperator).start(eq(skillTrendAggregationJob), org.mockito.ArgumentMatchers.argThat(parameters ->
                "2026-06-01".equals(parameters.getString("targetMonth"))
                        && parameters.getLong("requestedAt") == clock.millis()
        ));
    }
}
