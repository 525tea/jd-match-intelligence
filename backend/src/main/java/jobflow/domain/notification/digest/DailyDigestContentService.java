package jobflow.domain.notification.digest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.Job;
import jobflow.domain.job.JobRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.matching.JdMatchService;
import jobflow.domain.matching.dto.JdJobMatchResponse;
import jobflow.domain.recommendation.JobRecommendationService;
import jobflow.domain.recommendation.dto.JobRecommendationResponse;
import jobflow.domain.userjob.UserJobRepository;
import jobflow.domain.userjob.UserJobStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyDigestContentService {

    private static final int RECOMMENDATION_CANDIDATE_LIMIT = 8;
    private static final int JD_MATCH_CANDIDATE_LIMIT = 8;
    private static final int NEW_JOB_CANDIDATE_LIMIT = 5;
    private static final int DEADLINE_CANDIDATE_LIMIT = 8;
    private static final int NEW_JOB_LOOKBACK_HOURS = 24;
    private static final int DEADLINE_LOOKAHEAD_HOURS = 24;

    private static final String RECOMMENDATION_REASON = "추천 점수 기반";
    private static final String JD_MATCH_REASON = "JD 매칭 점수 기반";
    private static final String NEW_JOB_REASON = "오늘 새로 수집된 공고";
    private static final String DEADLINE_REASON = "마감 임박 저장 공고";

    private final JobRecommendationService jobRecommendationService;
    private final JdMatchService jdMatchService;
    private final JobRepository jobRepository;
    private final UserJobRepository userJobRepository;
    private final DailyDigestSectionAssembler sectionAssembler;
    private final Clock clock;

    public DailyDigestContentService(
            JobRecommendationService jobRecommendationService,
            JdMatchService jdMatchService,
            JobRepository jobRepository,
            UserJobRepository userJobRepository,
            DailyDigestSectionAssembler sectionAssembler,
            Clock clock
    ) {
        this.jobRecommendationService = jobRecommendationService;
        this.jdMatchService = jdMatchService;
        this.jobRepository = jobRepository;
        this.userJobRepository = userJobRepository;
        this.sectionAssembler = sectionAssembler;
        this.clock = clock;
    }

    public DailyDigestContent buildDigest(
            Long userId,
            Long userProjectId,
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        List<JobRecommendationResponse> recommendations = jobRecommendationService.recommendJobs(
                userId,
                userProjectId,
                targetRoles,
                RECOMMENDATION_CANDIDATE_LIMIT
        );
        List<JdJobMatchResponse> jdMatches = jdMatchService.findProjectJobMatches(
                userId,
                userProjectId,
                targetRoles,
                targetCareerLevel,
                JD_MATCH_CANDIDATE_LIMIT
        );

        Map<Long, Job> hydratedJobs = hydrateJobs(recommendations, jdMatches);

        return sectionAssembler.assemble(
                recommendations.stream()
                        .map(response -> toDigestItem(response, hydratedJobs.get(response.jobId())))
                        .toList(),
                jdMatches.stream()
                        .map(response -> toDigestItem(response, hydratedJobs.get(response.jobId())))
                        .toList(),
                findNewJobItems(now),
                findDeadlineJobItems(userId, now)
        );
    }

    private Map<Long, Job> hydrateJobs(
            List<JobRecommendationResponse> recommendations,
            List<JdJobMatchResponse> jdMatches
    ) {
        List<Long> jobIds = java.util.stream.Stream.concat(
                        recommendations.stream().map(JobRecommendationResponse::jobId),
                        jdMatches.stream().map(JdJobMatchResponse::jobId)
                )
                .distinct()
                .toList();

        if (jobIds.isEmpty()) {
            return Map.of();
        }

        return jobRepository.findByIdIn(jobIds).stream()
                .collect(Collectors.toMap(Job::getId, Function.identity()));
    }

    private List<DailyDigestJobItem> findNewJobItems(LocalDateTime now) {
        LocalDateTime from = now.minusHours(NEW_JOB_LOOKBACK_HOURS);
        return jobRepository
                .findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                        JobStatus.OPEN,
                        from,
                        now,
                        PageRequest.of(0, NEW_JOB_CANDIDATE_LIMIT)
                )
                .stream()
                .map(job -> toDigestItem(job, null, NEW_JOB_REASON))
                .toList();
    }

    private List<DailyDigestJobItem> findDeadlineJobItems(Long userId, LocalDateTime now) {
        return userJobRepository.findSavedOpenJobsDueSoon(
                        userId,
                        UserJobStatus.SAVED,
                        JobStatus.OPEN,
                        now,
                        now.plusHours(DEADLINE_LOOKAHEAD_HOURS),
                        PageRequest.of(0, DEADLINE_CANDIDATE_LIMIT)
                )
                .stream()
                .map(job -> toDigestItem(job, null, DEADLINE_REASON))
                .toList();
    }

    private DailyDigestJobItem toDigestItem(JobRecommendationResponse response, Job hydratedJob) {
        return new DailyDigestJobItem(
                response.jobId(),
                response.title(),
                response.companyName(),
                response.role(),
                response.careerLevel(),
                response.score().totalScore(),
                hydratedJob == null ? response.deadlineAt() : hydratedJob.getDeadlineAt(),
                originalUrl(hydratedJob),
                RECOMMENDATION_REASON
        );
    }

    private DailyDigestJobItem toDigestItem(JdJobMatchResponse response, Job hydratedJob) {
        return new DailyDigestJobItem(
                response.jobId(),
                response.title(),
                response.companyName(),
                response.role(),
                response.careerLevel(),
                response.score().totalScore(),
                hydratedJob == null ? null : hydratedJob.getDeadlineAt(),
                originalUrl(hydratedJob),
                JD_MATCH_REASON
        );
    }

    private DailyDigestJobItem toDigestItem(Job job, BigDecimal score, String reason) {
        return new DailyDigestJobItem(
                job.getId(),
                job.getTitle(),
                job.getCompanyName(),
                job.getRole(),
                job.getCareerLevel(),
                score,
                job.getDeadlineAt(),
                originalUrl(job),
                reason
        );
    }

    private String originalUrl(Job job) {
        if (job == null) {
            return null;
        }
        if (job.getOriginalUrl() != null && !job.getOriginalUrl().isBlank()) {
            return job.getOriginalUrl();
        }
        return job.getUrl();
    }
}
