package jobflow.domain.analytics;

import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "jobflow.analytics.skill-trend.runner",
        name = "enabled",
        havingValue = "true"
)
public class SkillTrendAggregationBatchRunner implements ApplicationRunner {

    private final SkillTrendAggregationBatchLauncher batchLauncher;
    private final Clock clock;

    @Value("${jobflow.analytics.skill-trend.runner.target-month:}")
    private String targetMonth;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LocalDate month = resolveTargetMonth();

        JobExecution jobExecution = batchLauncher.launchMonthly(month);

        log.info(
                "Skill trend aggregation batch runner completed. jobName={}, status={}, exitStatus={}, targetMonth={}",
                batchLauncher.jobName(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus(),
                month.withDayOfMonth(1)
        );
    }

    private LocalDate resolveTargetMonth() {
        if (StringUtils.hasText(targetMonth)) {
            return LocalDate.parse(targetMonth).withDayOfMonth(1);
        }
        return LocalDate.now(clock).withDayOfMonth(1);
    }
}
