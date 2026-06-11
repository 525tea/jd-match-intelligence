package jobflow.domain.analytics;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
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
        int normalizedLimit = Math.max(1, limit);
        List<Long> normalizedSkillIds = normalizeSkillIds(skillIds);

        return jobSkillIndexRepository.findOpenJobSkillMatchSummaries(
                normalizedSkillIds,
                PageRequest.of(0, normalizedLimit)
        );
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
}
