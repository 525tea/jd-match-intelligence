package jobflow.domain.analytics;

import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.job.JobRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Service
public class JobSkillIndexQueryService {

    private static final long NO_MATCHING_SKILL_ID = -1L;

    private final JobSkillIndexRepository jobSkillIndexRepository;

    public JobSkillIndexQueryService(JobSkillIndexRepository jobSkillIndexRepository) {
        this.jobSkillIndexRepository = jobSkillIndexRepository;
    }

    @Transactional(readOnly = true)
    public List<JobSkillMatchSummary> findTopOpenJobMatches(
            Collection<Long> skillIds,
            int limit
    ) {
        return findTopOpenJobMatches(skillIds, Arrays.asList(JobRole.values()), limit);
    }

    @Transactional(readOnly = true)
    public List<JobSkillMatchSummary> findTopOpenJobMatches(
            Collection<Long> skillIds,
            Collection<JobRole> targetRoles,
            int limit
    ) {
        int normalizedLimit = Math.max(1, limit);
        List<Long> normalizedSkillIds = normalizeSkillIds(skillIds);
        List<JobRole> normalizedTargetRoles = normalizeTargetRoles(targetRoles);

        return jobSkillIndexRepository.findOpenJobSkillMatchSummaries(
                normalizedSkillIds,
                normalizedTargetRoles
        ).stream()
                .sorted(Comparator.comparingDouble(JobSkillMatchSummary::matchScore).reversed()
                        .thenComparing(JobSkillMatchSummary::matchedRequiredSkillCount, Comparator.reverseOrder())
                        .thenComparing(JobSkillMatchSummary::jobId, Comparator.reverseOrder()))
                .limit(normalizedLimit)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobSkillMatchResponse> findTopOpenJobMatchResponses(
            Collection<Long> skillIds,
            Collection<JobRole> targetRoles,
            int limit
    ) {
        return findTopOpenJobMatches(skillIds, targetRoles, limit).stream()
                .map(JobSkillMatchResponse::from)
                .toList();
    }

    private List<Long> normalizeSkillIds(Collection<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of(NO_MATCHING_SKILL_ID);
        }

        List<Long> normalizedSkillIds = skillIds.stream()
                .filter(skillId -> skillId != null && skillId > 0)
                .distinct()
                .toList();

        if (normalizedSkillIds.isEmpty()) {
            return List.of(NO_MATCHING_SKILL_ID);
        }

        return normalizedSkillIds;
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
