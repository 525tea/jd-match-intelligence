package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import jobflow.domain.analytics.dto.JobMarketStatsResponse;
import jobflow.domain.analytics.dto.SkillCooccurrenceResponse;
import jobflow.domain.analytics.dto.SkillExperienceMarketResponse;
import jobflow.domain.analytics.dto.SkillTrendResponse;
import jobflow.domain.job.JobRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsTrendService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final SkillTrendRepository skillTrendRepository;
    private final SkillCooccurrenceRepository skillCooccurrenceRepository;
    private final SkillExperienceMarketRepository skillExperienceMarketRepository;
    private final JobMarketStatsRepository jobMarketStatsRepository;

    public List<SkillTrendResponse> getSkillTrends(LocalDate month, int limit) {
        LocalDate periodStart = resolvePeriodStart(month);

        return skillTrendRepository.findByPeriodTypeAndPeriodStartOrderByTrendScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart
                )
                .stream()
                .limit(normalizeLimit(limit))
                .map(SkillTrendResponse::from)
                .toList();
    }

    public List<SkillCooccurrenceResponse> getSkillCooccurrences(
            LocalDate month,
            Long skillId,
            int limit
    ) {
        LocalDate periodStart = resolvePeriodStart(month);

        return skillCooccurrenceRepository.findByPeriodTypeAndPeriodStartAndBaseSkillIdOrderByLiftScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        skillId
                )
                .stream()
                .limit(normalizeLimit(limit))
                .map(SkillCooccurrenceResponse::from)
                .toList();
    }

    public List<SkillExperienceMarketResponse> getSkillExperienceMarkets(
            LocalDate month,
            Long skillId,
            int limit
    ) {
        LocalDate periodStart = resolvePeriodStart(month);

        return skillExperienceMarketRepository.findByPeriodTypeAndPeriodStartAndSkillIdOrderByLiftScoreDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        skillId
                )
                .stream()
                .limit(normalizeLimit(limit))
                .map(SkillExperienceMarketResponse::from)
                .toList();
    }

    public List<JobMarketStatsResponse> getJobMarketStats(
            LocalDate month,
            JobRole role,
            int limit
    ) {
        LocalDate periodStart = resolvePeriodStart(month);

        return jobMarketStatsRepository.findByPeriodTypeAndPeriodStartAndRoleOrderByJobCountDesc(
                        AnalyticsPeriodType.MONTHLY,
                        periodStart,
                        role
                )
                .stream()
                .limit(normalizeLimit(limit))
                .map(JobMarketStatsResponse::from)
                .toList();
    }

    private LocalDate resolvePeriodStart(LocalDate month) {
        if (month == null) {
            return LocalDate.now().withDayOfMonth(1);
        }

        return month.withDayOfMonth(1);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
