package jobflow.domain.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.JobSkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SkillTrendAggregationBatchConfig {

    public static final String SKILL_TREND_AGGREGATION_JOB = "skillTrendAggregationJob";
    public static final String SKILL_TREND_CLEAR_STEP = "skillTrendClearStep";
    public static final String SKILL_TREND_AGGREGATION_STEP = "skillTrendAggregationStep";
    public static final String SKILL_MARKET_AGGREGATION_STEP = "skillMarketAggregationStep";

    private static final int SKILL_TREND_CHUNK_SIZE = 500;
    private final SkillMarketAggregationService skillMarketAggregationService;

    private final JobSkillRepository jobSkillRepository;
    private final SkillTrendRepository skillTrendRepository;

    @Bean(SKILL_TREND_AGGREGATION_JOB)
    public Job skillTrendAggregationJob(
            JobRepository batchJobRepository,
            @Qualifier(SKILL_TREND_CLEAR_STEP) Step skillTrendClearStep,
            @Qualifier(SKILL_TREND_AGGREGATION_STEP) Step skillTrendAggregationStep,
            @Qualifier(SKILL_MARKET_AGGREGATION_STEP) Step skillMarketAggregationStep
    ) {
        return new JobBuilder(SKILL_TREND_AGGREGATION_JOB, batchJobRepository)
                .start(skillTrendClearStep)
                .next(skillTrendAggregationStep)
                .next(skillMarketAggregationStep)
                .build();
    }

    @Bean(SKILL_TREND_CLEAR_STEP)
    public Step skillTrendClearStep(
            JobRepository batchJobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder(SKILL_TREND_CLEAR_STEP, batchJobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate periodStart = parseTargetMonth(
                            chunkContext.getStepContext().getJobParameters().get("targetMonth").toString()
                    );

                    skillTrendRepository.deleteByPeriodTypeAndPeriodStart(
                            AnalyticsPeriodType.MONTHLY,
                            periodStart
                    );

                    log.info("Skill trend clear step completed. periodType={}, periodStart={}",
                            AnalyticsPeriodType.MONTHLY, periodStart);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean(SKILL_TREND_AGGREGATION_STEP)
    public Step skillTrendAggregationStep(
            JobRepository batchJobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<JobSkillTrendAggregate> skillTrendAggregationReader,
            ItemProcessor<JobSkillTrendAggregate, SkillTrend> skillTrendAggregationProcessor,
            ItemWriter<SkillTrend> skillTrendAggregationWriter
    ) {
        return new StepBuilder(SKILL_TREND_AGGREGATION_STEP, batchJobRepository)
                .<JobSkillTrendAggregate, SkillTrend>chunk(SKILL_TREND_CHUNK_SIZE)
                .reader(skillTrendAggregationReader)
                .processor(skillTrendAggregationProcessor)
                .writer(skillTrendAggregationWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean(SKILL_MARKET_AGGREGATION_STEP)
    public Step skillMarketAggregationStep(
            JobRepository batchJobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder(SKILL_MARKET_AGGREGATION_STEP, batchJobRepository)
                .tasklet((contribution, chunkContext) -> {
                    LocalDate periodStart = parseTargetMonth(
                            chunkContext.getStepContext().getJobParameters().get("targetMonth").toString()
                    );

                    SkillMarketAggregationResult result = skillMarketAggregationService.aggregateMonthly(periodStart);

                    log.info(
                            "Skill market aggregation step completed. periodType={}, periodStart={}, "
                                    + "cooccurrenceSourceCount={}, cooccurrenceSavedCount={}, "
                                    + "skillExperienceSourceCount={}, skillExperienceSavedCount={}",
                            result.periodType(),
                            result.periodStart(),
                            result.cooccurrenceSourceCount(),
                            result.cooccurrenceSavedCount(),
                            result.skillExperienceSourceCount(),
                            result.skillExperienceSavedCount()
                    );

                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    @StepScope
    public ItemReader<JobSkillTrendAggregate> skillTrendAggregationReader(
            @Value("#{jobParameters['targetMonth']}") String targetMonth
    ) {
        LocalDate periodStart = parseTargetMonth(targetMonth);
        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodStart.plusMonths(1).atStartOfDay();

        List<JobSkillTrendAggregate> aggregates = jobSkillRepository.aggregateSkillTrends(from, to);
        return new ListItemReader<>(aggregates);
    }

    @Bean
    @StepScope
    public ItemProcessor<JobSkillTrendAggregate, SkillTrend> skillTrendAggregationProcessor(
            @Value("#{jobParameters['targetMonth']}") String targetMonth
    ) {
        LocalDate periodStart = parseTargetMonth(targetMonth);

        return aggregate -> SkillTrend.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                aggregate.skill(),
                aggregate.jobCount(),
                aggregate.requiredCount(),
                aggregate.preferredCount(),
                calculateTrendScore(aggregate)
        );
    }

    @Bean
    public ItemWriter<SkillTrend> skillTrendAggregationWriter() {
        return chunk -> skillTrendRepository.saveAll(chunk.getItems());
    }

    private static LocalDate parseTargetMonth(String targetMonth) {
        return LocalDate.parse(targetMonth).withDayOfMonth(1);
    }

    private static BigDecimal calculateTrendScore(JobSkillTrendAggregate aggregate) {
        return BigDecimal.valueOf(aggregate.requiredCount() * 2L + aggregate.preferredCount());
    }
}
