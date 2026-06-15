package jobflow.domain.gap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.List;
import jobflow.domain.analytics.JobSkillIndexQueryService;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapAnalysisResponse;
import jobflow.domain.gap.dto.GapJobMatchEvidenceResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.project.ProjectSkillSnapshotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GapAnalysisServiceTest {

    private final ProjectSkillSnapshotService projectSkillSnapshotService =
            mock(ProjectSkillSnapshotService.class);

    private final JobSkillIndexQueryService jobSkillIndexQueryService =
            mock(JobSkillIndexQueryService.class);

    private final GapAnalysisEvidenceService gapAnalysisEvidenceService =
            mock(GapAnalysisEvidenceService.class);

    private final GapAnalysisService gapAnalysisService =
            new GapAnalysisService(
                    projectSkillSnapshotService,
                    jobSkillIndexQueryService,
                    gapAnalysisEvidenceService
            );

    @Test
    @DisplayName("사용자 프로젝트 skill snapshot으로 공고 required/preferred 갭 분석을 수행한다")
    void analyzeProjectSkillGap() {
        Long userId = 1L;
        Long userProjectId = 10L;
        List<Long> skillIds = List.of(1L, 2L, 3L);
        List<JobRole> targetRoles = List.of(JobRole.BACKEND, JobRole.FULLSTACK);
        JobSkillMatchResponse matchResponse = jobSkillMatchResponse();
        GapJobMatchEvidenceResponse evidence = new GapJobMatchEvidenceResponse(
                43,
                List.of(),
                List.of(),
                List.of()
        );

        given(projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId))
                .willReturn(skillIds);
        given(jobSkillIndexQueryService.findTopOpenJobMatchResponses(skillIds, targetRoles, 20))
                .willReturn(List.of(matchResponse));
        given(gapAnalysisEvidenceService.buildEvidence(matchResponse))
                .willReturn(evidence);

        GapAnalysisResponse response = gapAnalysisService.analyzeProjectSkillGap(
                userId,
                userProjectId,
                targetRoles,
                20
        );

        assertThat(response.userProjectId()).isEqualTo(userProjectId);
        assertThat(response.userSkillIds()).containsExactly(1L, 2L, 3L);
        assertThat(response.jobMatches()).hasSize(1);
        assertThat(response.jobMatches().get(0).jobId()).isEqualTo(matchResponse.jobId());
        assertThat(response.jobMatches().get(0).missingRequiredSkills())
                .containsExactly("Kubernetes");
        assertThat(response.jobMatches().get(0).missingPreferredSkills())
                .containsExactly("Kafka");
        assertThat(response.jobMatches().get(0).evidence()).isEqualTo(evidence);
        assertThat(response.jobMatches().get(0).evidence().addedJobs()).isEqualTo(43);

        verify(projectSkillSnapshotService).findLatestSkillIds(userId, userProjectId);
        verify(jobSkillIndexQueryService).findTopOpenJobMatchResponses(skillIds, targetRoles, 20);
        verify(gapAnalysisEvidenceService).buildEvidence(matchResponse);
    }

    @Test
    @DisplayName("사용자 프로젝트 skill snapshot이 비어 있으면 공고 매칭 조회를 건너뛴다")
    void analyzeProjectSkillGapWithoutProjectSkills() {
        Long userId = 1L;
        Long userProjectId = 10L;
        given(projectSkillSnapshotService.findLatestSkillIds(userId, userProjectId))
                .willReturn(List.of());

        GapAnalysisResponse response = gapAnalysisService.analyzeProjectSkillGap(
                userId,
                userProjectId,
                List.of(JobRole.BACKEND),
                20
        );

        assertThat(response.userProjectId()).isEqualTo(userProjectId);
        assertThat(response.userSkillIds()).isEmpty();
        assertThat(response.jobMatches()).isEmpty();

        verify(projectSkillSnapshotService).findLatestSkillIds(userId, userProjectId);
        verifyNoInteractions(jobSkillIndexQueryService, gapAnalysisEvidenceService);
    }

    private JobSkillMatchResponse jobSkillMatchResponse() {
        return new JobSkillMatchResponse(
                100L,
                "백엔드 개발자",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                3,
                2,
                1,
                BigDecimal.valueOf(66.67),
                2,
                1,
                1,
                BigDecimal.valueOf(50.00),
                BigDecimal.valueOf(56.33),
                List.of("Java", "Spring Boot"),
                List.of("Kubernetes"),
                List.of("Docker"),
                List.of("Kafka")
        );
    }
}
