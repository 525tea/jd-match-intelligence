package jobflow.domain.analytics;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillTrendAggregationScheduler {

    private final JobOperator jobOperator;

    @Qualifier(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_JOB)
    private final Job skillTrendAggregationJob;

    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${jobflow.analytics.skill-trend.fixed-delay:3600000}",
            initialDelayString = "${jobflow.analytics.skill-trend.initial-delay:60000}"
    )
    public void aggregateCurrentMonthSkillTrends() {
        LocalDate month = LocalDate.now(clock);
        try {
            JobExecution jobExecution = jobOperator.start(
                    skillTrendAggregationJob,
                    new JobParametersBuilder()
                            .addString("targetMonth", month.withDayOfMonth(1).toString())
                            .addLong("requestedAt", clock.millis())
                            .toJobParameters()
            );

            log.info(
                    "Skill trend aggregation batch launched. jobName={}, status={}, exitStatus={}, targetMonth={}",
                    skillTrendAggregationJob.getName(),
                    jobExecution.getStatus(),
                    jobExecution.getExitStatus(),
                    month.withDayOfMonth(1)
            );
        } catch (Exception exception) {
            log.warn(
                    "Skill trend aggregation batch launch failed. jobName={}, targetMonth={}, error={}",
                    skillTrendAggregationJob.getName(),
                    month.withDayOfMonth(1),
                    exception.getMessage(),
                    exception
            );
        }
    }
}
