package jobflow.domain.gap;

import java.util.Collection;
import java.util.List;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapAnalysisResponse;
import jobflow.domain.gap.dto.GapJobMatchEvidenceResponse;
import jobflow.domain.gap.dto.GapJobMatchResponse;
import jobflow.domain.job.JobRole;
import jobflow.domain.project.ProjectSkillSnapshotService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GapAnalysisService {

    private final ProjectSkillSnapshotService projectSkillSnapshotService;
    private final JobSkillIndexQueryService jobSkillIndexQueryService;
    private final GapAnalysisEvidenceService gapAnalysisEvidenceService;

    public GapAnalysisService(
            ProjectSkillSnapshotService projectSkillSnapshotService,
            JobSkillIndexQueryService jobSkillIndexQueryService,
            GapAnalysisEvidenceService gapAnalysisEvidenceService
    ) {
        this.projectSkillSnapshotService = projectSkillSnapshotService;
        this.jobSkillIndexQueryService = jobSkillIndexQueryService;
        this.gapAnalysisEvidenceService = gapAnalysisEvidenceService;
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

        List<GapJobMatchResponse> gapJobMatches = jobMatches.stream()
                .map(this::toGapJobMatchResponse)
                .toList();

        return new GapAnalysisResponse(userProjectId, userSkillIds, gapJobMatches);
    }

    private GapJobMatchResponse toGapJobMatchResponse(JobSkillMatchResponse matchResponse) {
        GapJobMatchEvidenceResponse evidence = gapAnalysisEvidenceService.buildEvidence(matchResponse);
        return GapJobMatchResponse.from(matchResponse, evidence);
    }
}
