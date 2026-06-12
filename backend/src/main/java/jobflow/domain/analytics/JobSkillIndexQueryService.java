package jobflow.domain.analytics;

import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.RequirementType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        return findTopOpenJobMatchDetails(skillIds, targetRoles, limit).stream()
                .map(JobSkillMatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JobSkillMatchDetail> findTopOpenJobMatchDetails(
            Collection<Long> skillIds,
            Collection<JobRole> targetRoles,
            int limit
    ) {
        List<JobSkillMatchSummary> summaries = findTopOpenJobMatches(skillIds, targetRoles, limit);
        if (summaries.isEmpty()) {
            return List.of();
        }

        Set<Long> userSkillIds = normalizeSkillIds(skillIds).stream()
                .filter(skillId -> skillId != NO_MATCHING_SKILL_ID)
                .collect(Collectors.toSet());
        List<Long> jobIds = summaries.stream()
                .map(JobSkillMatchSummary::jobId)
                .toList();
        Map<Long, List<JobSkillIndex>> indexesByJobId = jobSkillIndexRepository.findByJobIdIn(jobIds).stream()
                .collect(Collectors.groupingBy(index -> index.getJob().getId()));

        return summaries.stream()
                .map(summary -> toDetail(summary, indexesByJobId.getOrDefault(summary.jobId(), List.of()), userSkillIds))
                .toList();
    }

    private JobSkillMatchDetail toDetail(
            JobSkillMatchSummary summary,
            List<JobSkillIndex> indexes,
            Set<Long> userSkillIds
    ) {
        return new JobSkillMatchDetail(
                summary,
                skillNames(indexes, RequirementType.REQUIRED, userSkillIds, true),
                skillNames(indexes, RequirementType.REQUIRED, userSkillIds, false),
                skillNames(indexes, RequirementType.PREFERRED, userSkillIds, true),
                skillNames(indexes, RequirementType.PREFERRED, userSkillIds, false)
        );
    }

    private List<String> skillNames(
            List<JobSkillIndex> indexes,
            RequirementType requirementType,
            Set<Long> userSkillIds,
            boolean matched
    ) {
        return indexes.stream()
                .filter(index -> index.getRequirementType() == requirementType)
                .filter(index -> userSkillIds.contains(index.getSkill().getId()) == matched)
                .map(index -> index.getSkill().getName())
                .distinct()
                .sorted()
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
