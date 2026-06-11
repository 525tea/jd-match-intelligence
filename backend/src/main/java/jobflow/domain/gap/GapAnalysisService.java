package jobflow.domain.gap;

import java.util.Collection;
import java.util.List;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapAnalysisResponse;
import jobflow.domain.job.JobRole;
import jobflow.domain.project.ProjectSkillSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GapAnalysisService {

    private final ProjectSkillSnapshotService projectSkillSnapshotService;
    private final JobSkillIndexQueryService jobSkillIndexQueryService;

    public GapAnalysisService(
            ProjectSkillSnapshotService projectSkillSnapshotService,
            JobSkillIndexQueryService jobSkillIndexQueryService
    ) {
        this.projectSkillSnapshotService = projectSkillSnapshotService;
        this.jobSkillIndexQueryService = jobSkillIndexQueryService;
    }

    public GapAnalysisResponse analyzeProjectSkillGap(
            Long userId,
            Long userProjectId,
            Collection<JobRole> targetRoles,
            int limit
    ) {
        List<Long> userSkillIds = projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId);
        if (userSkillIds.isEmpty()) {
            return new GapAnalysisResponse(userProjectId, userSkillIds, List.of());
        }

        List<JobSkillMatchResponse> jobMatches = jobSkillIndexQueryService.findTopOpenJobMatchResponses(
                userSkillIds,
                targetRoles,
                limit
        );

        return new GapAnalysisResponse(userProjectId, userSkillIds, jobMatches);
    }
}
