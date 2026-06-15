package jobflow.domain.recommendation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.project.ProjectSkillSnapshotService;
import jobflow.domain.recommendation.dto.JobRecommendationResponse;
import jobflow.domain.recommendation.dto.JobRecommendationScoreResponse;
import jobflow.domain.userjob.UserJob;
import jobflow.domain.userjob.UserJobRepository;
import jobflow.domain.userjob.UserJobStatus;
import jobflow.global.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JobRecommendationService {

    private static final int CANDIDATE_MULTIPLIER = 3;
    private static final int DEFAULT_COLD_START_CANDIDATE_LIMIT = 50;

    private final ProjectSkillSnapshotService projectSkillSnapshotService;
    private final JobSkillIndexQueryService jobSkillIndexQueryService;
    private final JobRepository jobRepository;
    private final UserJobRepository userJobRepository;
    private final RecommendationScoreCalculator recommendationScoreCalculator;

    public JobRecommendationService(
            ProjectSkillSnapshotService projectSkillSnapshotService,
            JobSkillIndexQueryService jobSkillIndexQueryService,
            JobRepository jobRepository,
            UserJobRepository userJobRepository,
            RecommendationScoreCalculator recommendationScoreCalculator
    ) {
        this.projectSkillSnapshotService = projectSkillSnapshotService;
        this.jobSkillIndexQueryService = jobSkillIndexQueryService;
        this.jobRepository = jobRepository;
        this.userJobRepository = userJobRepository;
        this.recommendationScoreCalculator = recommendationScoreCalculator;
    }

    @Cacheable(
            cacheNames = CacheNames.JOB_RECOMMENDATION,
            key = "T(jobflow.domain.recommendation.JobRecommendationService).recommendationCacheKey(#userId, #userProjectId, #targetRoles, #limit)"
    )
    public List<JobRecommendationResponse> recommendJobs(
            Long userId,
            Long userProjectId,
            Collection<JobRole> targetRoles,
            int limit
    ) {
        int normalizedLimit = Math.max(1, limit);
        List<Long> userSkillIds = projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId);
        int candidateLimit = Math.max(DEFAULT_COLD_START_CANDIDATE_LIMIT, normalizedLimit * CANDIDATE_MULTIPLIER);

        List<JobSkillMatchResponse> matchResponses = jobSkillIndexQueryService.findTopOpenJobMatchResponses(
                userSkillIds,
                targetRoles,
                candidateLimit
        );
        if (matchResponses.isEmpty()) {
            return List.of();
        }

        List<Long> jobIds = matchResponses.stream()
                .map(JobSkillMatchResponse::jobId)
                .toList();

        Map<Long, Job> jobById = jobRepository.findByIdIn(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
        Map<Long, UserJobStatus> userJobStatusByJobId = userJobRepository.findByUserIdAndJobIdIn(userId, jobIds).stream()
                .collect(Collectors.toMap(
                        userJob -> userJob.getJob().getId(),
                        UserJob::getStatus,
                        (left, right) -> left
                ));

        boolean coldStart = userSkillIds.isEmpty();
        LocalDateTime now = LocalDateTime.now();

        return matchResponses.stream()
                .map(matchResponse -> toRecommendation(
                        matchResponse,
                        jobById.get(matchResponse.jobId()),
                        userJobStatusByJobId.get(matchResponse.jobId()),
                        coldStart,
                        now
                ))
                .filter(response -> response != null)
                .filter(response -> response.userJobStatus() != UserJobStatus.IGNORED)
                .sorted(Comparator.comparing(
                                (JobRecommendationResponse response) -> response.score().totalScore()
                        ).reversed()
                        .thenComparing(JobRecommendationResponse::jobId, Comparator.reverseOrder()))
                .limit(normalizedLimit)
                .toList();
    }

    public static String recommendationCacheKey(
            Long userId,
            Long userProjectId,
            Collection<JobRole> targetRoles,
            int limit
    ) {
        return "userId=" + userId
                + ":projectId=" + userProjectId
                + ":roles=" + normalizeRolesForCache(targetRoles)
                + ":limit=" + Math.max(1, limit);
    }

    private static String normalizeRolesForCache(Collection<JobRole> targetRoles) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return "ALL";
        }
        return targetRoles.stream()
                .map(JobRole::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private JobRecommendationResponse toRecommendation(
            JobSkillMatchResponse matchResponse,
            Job job,
            UserJobStatus userJobStatus,
            boolean coldStart,
            LocalDateTime now
    ) {
        if (job == null) {
            return null;
        }

        RecommendationScoreBreakdown scoreBreakdown = recommendationScoreCalculator.calculate(
                new RecommendationScoreInput(
                        skillMatchRate(matchResponse, coldStart),
                        freshnessBaseAt(job),
                        job.getDeadlineAt(),
                        behaviorSignal(userJobStatus),
                        popularityRate(matchResponse),
                        now
                )
        );

        return JobRecommendationResponse.from(
                job,
                matchResponse,
                userJobStatus,
                JobRecommendationScoreResponse.from(scoreBreakdown)
        );
    }

    private Double skillMatchRate(JobSkillMatchResponse matchResponse, boolean coldStart) {
        if (coldStart) {
            return null;
        }

        if (matchResponse.requiredSkillCount() == 0 && matchResponse.preferredSkillCount() == 0) {
            return null;
        }

        double requiredRate = percentToRate(matchResponse.requiredMatchRate());
        double preferredRate = percentToRate(matchResponse.preferredMatchRate());
        if (matchResponse.requiredSkillCount() == 0) {
            return preferredRate;
        }
        if (matchResponse.preferredSkillCount() == 0) {
            return requiredRate;
        }
        return requiredRate * 0.70 + preferredRate * 0.30;
    }

    private double percentToRate(BigDecimal percentRate) {
        if (percentRate == null) {
            return 0.0;
        }
        return percentRate.doubleValue() / 100.0;
    }

    private Double popularityRate(JobSkillMatchResponse matchResponse) {
        long totalSkillCount = matchResponse.requiredSkillCount() + matchResponse.preferredSkillCount();
        if (totalSkillCount <= 0) {
            return 0.0;
        }
        return Math.min(totalSkillCount / 10.0, 1.0);
    }

    private LocalDateTime freshnessBaseAt(Job job) {
        if (job.getCreatedAt() != null) {
            return job.getCreatedAt();
        }
        return job.getOpenedAt();
    }

    private RecommendationBehaviorSignal behaviorSignal(UserJobStatus status) {
        if (status == UserJobStatus.VIEWED) {
            return RecommendationBehaviorSignal.VIEWED;
        }
        if (status == UserJobStatus.SAVED) {
            return RecommendationBehaviorSignal.SAVED;
        }
        return RecommendationBehaviorSignal.NONE;
    }
}
