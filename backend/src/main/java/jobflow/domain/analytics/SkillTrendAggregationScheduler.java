package jobflow.domain.analytics;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillTrendAggregationScheduler {

    private final SkillTrendAggregationBatchLauncher batchLauncher;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${jobflow.analytics.skill-trend.fixed-delay:3600000}",
            initialDelayString = "${jobflow.analytics.skill-trend.initial-delay:60000}"
    )
    public void aggregateCurrentMonthSkillTrends() {
        LocalDate month = LocalDate.now(clock);
        LocalDate periodStart = month.withDayOfMonth(1);

        try {
            JobExecution jobExecution = batchLauncher.launchMonthly(periodStart);

            log.info(
                    "Skill trend aggregation batch launched. jobName={}, status={}, exitStatus={}, targetMonth={}",
                    batchLauncher.jobName(),
                    jobExecution.getStatus(),
                    jobExecution.getExitStatus(),
                    periodStart
            );
        } catch (Exception exception) {
            log.warn(
                    "Skill trend aggregation batch launch failed. jobName={}, targetMonth={}, error={}",
                    batchLauncher.jobName(),
                    periodStart,
                    exception.getMessage(),
                    exception
            );
        }
    }
}
