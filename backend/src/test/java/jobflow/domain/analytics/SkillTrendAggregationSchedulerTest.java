package jobflow.domain.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillTrendAggregationSchedulerTest {

    @Test
    @DisplayName("스케줄러는 현재 월 스킬 트렌드 집계를 서비스에 위임한다")
    void aggregateCurrentMonthSkillTrends() {
        SkillTrendAggregationService skillTrendAggregationService = mock(SkillTrendAggregationService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-06-07T01:30:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        SkillTrendAggregationScheduler scheduler = new SkillTrendAggregationScheduler(
                skillTrendAggregationService,
                clock
        );

        given(skillTrendAggregationService.aggregateMonthly(any()))
                .willReturn(new SkillTrendAggregationResult(
                        AnalyticsPeriodType.MONTHLY,
                        LocalDate.of(2026, 6, 1),
                        2,
                        2
                ));

        scheduler.aggregateCurrentMonthSkillTrends();

        verify(skillTrendAggregationService).aggregateMonthly(LocalDate.of(2026, 6, 7));
    }
}
