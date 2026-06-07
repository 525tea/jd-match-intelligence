package jobflow.domain.analytics;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SkillTrendAggregationBatchConfig {

    public static final String SKILL_TREND_AGGREGATION_JOB = "skillTrendAggregationJob";
    public static final String SKILL_TREND_AGGREGATION_STEP = "skillTrendAggregationStep";

    private final SkillTrendAggregationService skillTrendAggregationService;

    @Bean(SKILL_TREND_AGGREGATION_JOB)
    public Job skillTrendAggregationJob(
            JobRepository batchJobRepository,
            Step skillTrendAggregationStep
    ) {
        return new JobBuilder(SKILL_TREND_AGGREGATION_JOB, batchJobRepository)
                .start(skillTrendAggregationStep)
                .build();
    }

    @Bean(SKILL_TREND_AGGREGATION_STEP)
    public Step skillTrendAggregationStep(
            JobRepository batchJobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder(SKILL_TREND_AGGREGATION_STEP, batchJobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String targetMonth = chunkContext.getStepContext()
                            .getJobParameters()
                            .get("targetMonth")
                            .toString();
                    SkillTrendAggregationResult result = skillTrendAggregationService.aggregateMonthly(
                            LocalDate.parse(targetMonth)
                    );
                    log.info(
                            "Skill trend aggregation batch step completed. periodType={}, periodStart={}, sourceCount={}, savedCount={}",
                            result.periodType(),
                            result.periodStart(),
                            result.sourceCount(),
                            result.savedCount()
                    );
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
}
