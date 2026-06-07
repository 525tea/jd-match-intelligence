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

class SkillTrendAggregationSchedulerTest {

    @Test
    @DisplayName("스케줄러는 현재 월 스킬 트렌드 Batch Job을 실행한다")
    void aggregateCurrentMonthSkillTrends() throws Exception {
        SkillTrendAggregationBatchLauncher batchLauncher = mock(SkillTrendAggregationBatchLauncher.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        when(batchLauncher.jobName()).thenReturn("skillTrendAggregationJob");

        SkillTrendAggregationScheduler scheduler = new SkillTrendAggregationScheduler(
                batchLauncher,
                clock
        );

        scheduler.aggregateCurrentMonthSkillTrends();

        verify(batchLauncher).launchMonthly(LocalDate.of(2026, 6, 1));
    }
}
