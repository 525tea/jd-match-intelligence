package jobflow.domain.analytics;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SkillTrendAggregationBatchLauncher {

    private final JobOperator jobOperator;

    @Qualifier(SkillTrendAggregationBatchConfig.SKILL_TREND_AGGREGATION_JOB)
    private final Job skillTrendAggregationJob;

    private final Clock clock;

    public JobExecution launchMonthly(LocalDate month) throws Exception {
        LocalDate periodStart = month.withDayOfMonth(1);

        return jobOperator.start(
                skillTrendAggregationJob,
                new JobParametersBuilder()
                        .addString("targetMonth", periodStart.toString())
                        .addLong("requestedAt", clock.millis())
                        .toJobParameters()
        );
    }

    public String jobName() {
        return skillTrendAggregationJob.getName();
    }
}
