package jobflow.domain.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jobflow.domain.job.JobExperienceTagRepository;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobSkillRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.domain.skill.Skill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SkillMarketAggregationService {

    private final JobRepository jobRepository;
    private final JobSkillRepository jobSkillRepository;
    private final JobExperienceTagRepository jobExperienceTagRepository;
    private final SkillCooccurrenceRepository skillCooccurrenceRepository;
    private final SkillExperienceMarketRepository skillExperienceMarketRepository;

    @Transactional
    public SkillMarketAggregationResult aggregateMonthly(LocalDate month) {
        LocalDate periodStart = month.withDayOfMonth(1);
        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodStart.plusMonths(1).atStartOfDay();
        long totalJobCount = jobRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);

        List<JobSkillTrendAggregate> skillAggregates = jobSkillRepository.aggregateSkillTrends(from, to);
        Map<Long, JobSkillTrendAggregate> skillAggregateById = skillAggregates.stream()
                .collect(Collectors.toMap(aggregate -> aggregate.skill().getId(), Function.identity()));

        List<ExperienceTagMarketAggregate> tagAggregates =
                jobExperienceTagRepository.aggregateExperienceTagMarkets(from, to);
        Map<String, ExperienceTagMarketAggregate> tagAggregateByCode = tagAggregates.stream()
                .collect(Collectors.toMap(aggregate -> aggregate.tagCode().getCode(), Function.identity()));

        List<JobSkillCooccurrenceAggregate> cooccurrenceAggregates =
                jobSkillRepository.aggregateSkillCooccurrences(from, to);
        List<JobSkillExperienceMarketAggregate> skillExperienceAggregates =
                jobExperienceTagRepository.aggregateSkillExperienceMarkets(from, to);

        skillCooccurrenceRepository.deleteByPeriodTypeAndPeriodStart(AnalyticsPeriodType.MONTHLY, periodStart);
        skillExperienceMarketRepository.deleteByPeriodTypeAndPeriodStart(AnalyticsPeriodType.MONTHLY, periodStart);

        List<SkillCooccurrence> cooccurrences = cooccurrenceAggregates.stream()
                .map(aggregate -> toSkillCooccurrence(
                        periodStart,
                        aggregate,
                        skillAggregateById,
                        totalJobCount
                ))
                .toList();

        List<SkillExperienceMarket> skillExperienceMarkets = skillExperienceAggregates.stream()
                .map(aggregate -> toSkillExperienceMarket(
                        periodStart,
                        aggregate,
                        skillAggregateById,
                        tagAggregateByCode,
                        totalJobCount
                ))
                .toList();

        skillCooccurrenceRepository.saveAll(cooccurrences);
        skillExperienceMarketRepository.saveAll(skillExperienceMarkets);

        return new SkillMarketAggregationResult(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                cooccurrenceAggregates.size(),
                cooccurrences.size(),
                skillExperienceAggregates.size(),
                skillExperienceMarkets.size()
        );
    }

    private SkillCooccurrence toSkillCooccurrence(
            LocalDate periodStart,
            JobSkillCooccurrenceAggregate aggregate,
            Map<Long, JobSkillTrendAggregate> skillAggregateById,
            long totalJobCount
    ) {
        Skill baseSkill = aggregate.baseSkill();
        Skill coSkill = aggregate.coSkill();
        long baseSkillJobCount = skillAggregateById.get(baseSkill.getId()).jobCount();
        long coSkillJobCount = skillAggregateById.get(coSkill.getId()).jobCount();

        return SkillCooccurrence.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                baseSkill,
                coSkill,
                aggregate.cooccurrenceCount(),
                baseSkillJobCount,
                coSkillJobCount,
                calculateLiftScore(
                        aggregate.cooccurrenceCount(),
                        baseSkillJobCount,
                        coSkillJobCount,
                        totalJobCount
                )
        );
    }

    private SkillExperienceMarket toSkillExperienceMarket(
            LocalDate periodStart,
            JobSkillExperienceMarketAggregate aggregate,
            Map<Long, JobSkillTrendAggregate> skillAggregateById,
            Map<String, ExperienceTagMarketAggregate> tagAggregateByCode,
            long totalJobCount
    ) {
        Skill skill = aggregate.skill();
        ExperienceTagCode tagCode = aggregate.tagCode();
        long skillJobCount = skillAggregateById.get(skill.getId()).jobCount();
        long tagJobCount = tagAggregateByCode.get(tagCode.getCode()).jobCount();

        return SkillExperienceMarket.create(
                AnalyticsPeriodType.MONTHLY,
                periodStart,
                skill,
                tagCode,
                aggregate.jobCount(),
                skillJobCount,
                tagJobCount,
                calculateLiftScore(
                        aggregate.jobCount(),
                        skillJobCount,
                        tagJobCount,
                        totalJobCount
                )
        );
    }

    private BigDecimal calculateLiftScore(
            long pairCount,
            long leftCount,
            long rightCount,
            long totalJobCount
    ) {
        if (pairCount == 0 || leftCount == 0 || rightCount == 0 || totalJobCount == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal numerator = BigDecimal.valueOf(pairCount)
                .multiply(BigDecimal.valueOf(totalJobCount));
        BigDecimal denominator = BigDecimal.valueOf(leftCount)
                .multiply(BigDecimal.valueOf(rightCount));

        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }
}
