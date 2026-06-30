package jobflow.domain.matching;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.JobSkillMatchDetail;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobExperienceTag;
import jobflow.domain.job.JobExperienceTagRepository;
import jobflow.domain.job.JobRole;
import jobflow.domain.matching.dto.JdJobMatchResponse;
import jobflow.domain.matching.dto.JdMatchExperienceTagResponse;
import jobflow.domain.project.UserProjectAnalysis;
import jobflow.domain.project.UserProjectAnalysisRepository;
import jobflow.domain.project.UserProjectExperienceTag;
import jobflow.domain.project.UserProjectExperienceTagRepository;
import jobflow.domain.project.UserProjectRepository;
import jobflow.domain.project.UserProjectSkillRepository;
import jobflow.domain.skill.ExperienceTagCode;
import jobflow.global.cache.CacheNames;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import jobflow.global.error.exception.EntityNotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JdMatchService {

    private static final int MAX_LIMIT = 50;
    private static final int MIN_CANDIDATE_LIMIT = 50;
    private static final int CANDIDATE_LIMIT_MULTIPLIER = 5;

    private final UserProjectRepository userProjectRepository;
    private final UserProjectAnalysisRepository userProjectAnalysisRepository;
    private final UserProjectSkillRepository userProjectSkillRepository;
    private final UserProjectExperienceTagRepository userProjectExperienceTagRepository;
    private final JobSkillIndexQueryService jobSkillIndexQueryService;
    private final JobExperienceTagRepository jobExperienceTagRepository;
    private final JdMatchScoreCalculator jdMatchScoreCalculator;

    public JdMatchService(
            UserProjectRepository userProjectRepository,
            UserProjectAnalysisRepository userProjectAnalysisRepository,
            UserProjectSkillRepository userProjectSkillRepository,
            UserProjectExperienceTagRepository userProjectExperienceTagRepository,
            JobSkillIndexQueryService jobSkillIndexQueryService,
            JobExperienceTagRepository jobExperienceTagRepository,
            JdMatchScoreCalculator jdMatchScoreCalculator
    ) {
        this.userProjectRepository = userProjectRepository;
        this.userProjectAnalysisRepository = userProjectAnalysisRepository;
        this.userProjectSkillRepository = userProjectSkillRepository;
        this.userProjectExperienceTagRepository = userProjectExperienceTagRepository;
        this.jobSkillIndexQueryService = jobSkillIndexQueryService;
        this.jobExperienceTagRepository = jobExperienceTagRepository;
        this.jdMatchScoreCalculator = jdMatchScoreCalculator;
    }

    @Cacheable(
            cacheNames = CacheNames.JD_MATCH,
            key = "T(jobflow.domain.matching.JdMatchService).jdMatchCacheKey("
                    + "#userId, #userProjectId, #targetRoles, #targetCareerLevel, #limit)",
            sync = true
    )
    public List<JdJobMatchResponse> findProjectJobMatches(
            Long userId,
            Long userProjectId,
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel,
            int limit
    ) {
        int normalizedLimit = normalizeLimit(limit);
        UserProjectAnalysis latestAnalysis = findLatestAnalysis(userId, userProjectId);

        List<Long> projectSkillIds = userProjectSkillRepository.findDistinctSkillIdsByAnalysisId(latestAnalysis.getId());
        List<UserProjectExperienceTag> projectExperienceTags =
                userProjectExperienceTagRepository.findByAnalysisIdWithTagCode(latestAnalysis.getId());
        Set<String> projectExperienceTagCodes = projectExperienceTags.stream()
                .map(UserProjectExperienceTag::getTagCode)
                .map(ExperienceTagCode::getCode)
                .collect(Collectors.toSet());

        if (projectSkillIds.isEmpty() && projectExperienceTagCodes.isEmpty()) {
            return List.of();
        }

        int candidateLimit = Math.max(MIN_CANDIDATE_LIMIT, normalizedLimit * CANDIDATE_LIMIT_MULTIPLIER);
        List<JobSkillMatchDetail> skillMatchDetails = jobSkillIndexQueryService.findTopOpenJobMatchDetails(
                projectSkillIds,
                normalizeTargetRoles(targetRoles),
                candidateLimit
        );
        if (skillMatchDetails.isEmpty()) {
            return List.of();
        }

        Map<Long, List<JobExperienceTag>> jobExperienceTagsByJobId = findJobExperienceTagsByJobId(skillMatchDetails);

        return skillMatchDetails.stream()
                .map(detail -> toResponse(
                        userProjectId,
                        latestAnalysis,
                        detail,
                        targetCareerLevel,
                        projectExperienceTagCodes,
                        jobExperienceTagsByJobId.getOrDefault(detail.summary().jobId(), List.of())
                ))
                .sorted(Comparator.comparing((JdJobMatchResponse response) -> response.score().totalScore()).reversed()
                        .thenComparing(JdJobMatchResponse::matchedRequiredSkillCount, Comparator.reverseOrder())
                        .thenComparing(JdJobMatchResponse::jobId, Comparator.reverseOrder()))
                .limit(normalizedLimit)
                .toList();
    }

    private UserProjectAnalysis findLatestAnalysis(Long userId, Long userProjectId) {
        if (userId == null || userProjectId == null) {
            throw new BusinessException(
                    ErrorCode.COMMON_INVALID_INPUT,
                    "사용자와 프로젝트 식별자는 필수입니다."
            );
        }

        if (!userProjectRepository.existsByIdAndUserId(userProjectId, userId)) {
            throw new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND);
        }

        return userProjectAnalysisRepository
                .findFirstByUserProjectIdAndUserProjectUserIdOrderByAnalyzedAtDescIdDesc(userProjectId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        ErrorCode.USER_PROJECT_NOT_FOUND,
                        "프로젝트 분석 결과를 찾을 수 없습니다."
                ));
    }

    private JdJobMatchResponse toResponse(
            Long userProjectId,
            UserProjectAnalysis analysis,
            JobSkillMatchDetail detail,
            CareerLevel targetCareerLevel,
            Set<String> projectExperienceTagCodes,
            List<JobExperienceTag> jobExperienceTags
    ) {
        List<JdMatchExperienceTagResponse> jobExperienceTagResponses = distinctExperienceTags(jobExperienceTags);
        List<JdMatchExperienceTagResponse> matchedExperienceTags = filterExperienceTags(
                jobExperienceTagResponses,
                projectExperienceTagCodes,
                true
        );
        List<JdMatchExperienceTagResponse> missingExperienceTags = filterExperienceTags(
                jobExperienceTagResponses,
                projectExperienceTagCodes,
                false
        );

        JdMatchScoreBreakdown scoreBreakdown = jdMatchScoreCalculator.calculate(new JdMatchScoreInput(
                detail.summary().requiredSkillCount(),
                detail.summary().matchedRequiredSkillCount(),
                detail.summary().preferredSkillCount(),
                detail.summary().matchedPreferredSkillCount(),
                jobExperienceTagResponses.size(),
                matchedExperienceTags.size(),
                targetCareerLevel,
                detail.summary().careerLevel(),
                analysis.getConfidenceScore()
        ));

        return JdJobMatchResponse.from(
                userProjectId,
                analysis,
                detail,
                scoreBreakdown,
                matchedExperienceTags,
                missingExperienceTags
        );
    }

    private Map<Long, List<JobExperienceTag>> findJobExperienceTagsByJobId(List<JobSkillMatchDetail> skillMatchDetails) {
        List<Long> jobIds = skillMatchDetails.stream()
                .map(detail -> detail.summary().jobId())
                .distinct()
                .toList();

        if (jobIds.isEmpty()) {
            return Map.of();
        }

        return jobExperienceTagRepository.findByJobIdInWithTagCode(jobIds).stream()
                .collect(Collectors.groupingBy(jobExperienceTag -> jobExperienceTag.getJob().getId()));
    }

    private List<JdMatchExperienceTagResponse> distinctExperienceTags(List<JobExperienceTag> jobExperienceTags) {
        Map<String, JdMatchExperienceTagResponse> responsesByCode = new LinkedHashMap<>();

        jobExperienceTags.stream()
                .map(JobExperienceTag::getTagCode)
                .sorted(Comparator.comparing(ExperienceTagCode::getCode))
                .forEach(tagCode -> responsesByCode.putIfAbsent(
                        tagCode.getCode(),
                        JdMatchExperienceTagResponse.from(tagCode)
                ));

        return List.copyOf(responsesByCode.values());
    }

    private List<JdMatchExperienceTagResponse> filterExperienceTags(
            List<JdMatchExperienceTagResponse> jobExperienceTags,
            Set<String> projectExperienceTagCodes,
            boolean matched
    ) {
        return jobExperienceTags.stream()
                .filter(tag -> projectExperienceTagCodes.contains(tag.code()) == matched)
                .toList();
    }

    public static String jdMatchCacheKey(
            Long userId,
            Long userProjectId,
            Collection<JobRole> targetRoles,
            CareerLevel targetCareerLevel,
            int limit
    ) {
        return "userId=" + userId
                + ":projectId=" + userProjectId
                + ":roles=" + normalizeTargetRoleKey(targetRoles)
                + ":career=" + normalizeCareerLevelKey(targetCareerLevel)
                + ":limit=" + normalizeLimitKey(limit);
    }

    private static String normalizeTargetRoleKey(Collection<JobRole> targetRoles) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return "ALL";
        }

        String roles = targetRoles.stream()
                .filter(role -> role != null)
                .map(JobRole::name)
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));

        if (roles.isBlank()) {
            return "ALL";
        }
        return roles;
    }

    private static String normalizeCareerLevelKey(CareerLevel targetCareerLevel) {
        if (targetCareerLevel == null) {
            return "ANY";
        }
        return targetCareerLevel.name();
    }

    private static int normalizeLimitKey(int limit) {
        return Math.min(Math.max(1, limit), MAX_LIMIT);
    }

    private int normalizeLimit(int limit) {
        return normalizeLimitKey(limit);
    }

    private List<JobRole> normalizeTargetRoles(Collection<JobRole> targetRoles) {
        if (targetRoles == null || targetRoles.isEmpty()) {
            return Arrays.asList(JobRole.values());
        }

        List<JobRole> normalizedTargetRoles = targetRoles.stream()
                .filter(role -> role != null)
                .distinct()
                .toList();

        if (normalizedTargetRoles.isEmpty()) {
            return Arrays.asList(JobRole.values());
        }

        return normalizedTargetRoles;
    }
}
