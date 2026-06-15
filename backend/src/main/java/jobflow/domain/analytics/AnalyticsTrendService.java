package jobflow.domain.analytics;

import java.time.LocalDate;
import java.util.List;
import jobflow.domain.analytics.dto.JobMarketStatsResponse;
import jobflow.domain.analytics.dto.SkillCooccurrenceResponse;
import jobflow.domain.analytics.dto.SkillExperienceMarketResponse;
import jobflow.domain.analytics.dto.SkillTrendResponse;
import jobflow.domain.job.JobRole;
import jobflow.global.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsTrendService {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final SkillTrendRepository skillTrendRepository;
    private final SkillCooccurrenceRepository skillCooccurrenceRepository;
    private final SkillExperienceMarketRepository skillExperienceMarketRepository;
    private final JobMarketStatsRepository jobMarketStatsRepository;

    @Cacheable(
            cacheNames = CacheNames.TREND_SKILLS,
            key = "T(jobflow.domain.analytics.AnalyticsTrendService).trendCacheKey(#month, #limit)"
    )
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

    @Cacheable(
            cacheNames = CacheNames.TREND_SKILL_COOCCURRENCES,
            key = "T(jobflow.domain.analytics.AnalyticsTrendService).trendSkillCacheKey(#month, #skillId, #limit)"
    )
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

    @Cacheable(
            cacheNames = CacheNames.TREND_SKILL_EXPERIENCE_TAGS,
            key = "T(jobflow.domain.analytics.AnalyticsTrendService).trendSkillCacheKey(#month, #skillId, #limit)"
    )
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

    @Cacheable(
            cacheNames = CacheNames.TREND_MARKET,
            key = "T(jobflow.domain.analytics.AnalyticsTrendService).trendMarketCacheKey(#month, #role, #limit)"
    )
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

    static String trendCacheKey(LocalDate month, int limit) {
        return resolvePeriodStartForCache(month) + ":limit=" + normalizeLimitForCache(limit);
    }

    static String trendSkillCacheKey(LocalDate month, Long skillId, int limit) {
        return resolvePeriodStartForCache(month)
                + ":skillId=" + skillId
                + ":limit=" + normalizeLimitForCache(limit);
    }

    static String trendMarketCacheKey(LocalDate month, JobRole role, int limit) {
        return resolvePeriodStartForCache(month)
                + ":role=" + role
                + ":limit=" + normalizeLimitForCache(limit);
    }

    private LocalDate resolvePeriodStart(LocalDate month) {
        return resolvePeriodStartForCache(month);
    }

    private int normalizeLimit(int limit) {
        return normalizeLimitForCache(limit);
    }

    private static LocalDate resolvePeriodStartForCache(LocalDate month) {
        if (month == null) {
            return LocalDate.now().withDayOfMonth(1);
        }

        return month.withDayOfMonth(1);
    }

    private static int normalizeLimitForCache(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
