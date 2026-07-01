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
import jobflow.global.cache.CacheNames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    @DisplayName("갭 분석 조회에 캐시를 적용한다")
    void analyzeProjectSkillGapUsesCache() throws NoSuchMethodException {
        Cacheable cacheable = GapAnalysisService.class
                .getMethod("analyzeProjectSkillGap", Long.class, Long.class, java.util.Collection.class, int.class)
                .getAnnotation(Cacheable.class);

        assertThat(cacheable.cacheNames()).containsExactly(CacheNames.GAP_ANALYSIS);
        assertThat(cacheable.key()).contains("gapAnalysisCacheKey");
        assertThat(cacheable.sync()).isTrue();
    }

    @Test
    @DisplayName("갭 분석 캐시 hit 경로는 DB 트랜잭션을 열지 않는다")
    void analyzeProjectSkillGapDoesNotOpenTransactionOnCacheHit() throws NoSuchMethodException {
        Transactional transactional = GapAnalysisService.class
                .getMethod("analyzeProjectSkillGap", Long.class, Long.class, java.util.Collection.class, int.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    @DisplayName("갭 분석 캐시 key는 사용자, 프로젝트, 정렬된 target role, limit을 포함한다")
    void gapAnalysisCacheKeyIncludesUserProjectRolesAndLimit() {
        String cacheKey = GapAnalysisService.gapAnalysisCacheKey(
                4L,
                2L,
                List.of(JobRole.FULLSTACK, JobRole.BACKEND),
                20
        );

        assertThat(cacheKey)
                .isEqualTo("userId=4:projectId=2:roles=BACKEND,FULLSTACK:limit=20");
    }

    @Test
    @DisplayName("target role이 없으면 갭 분석 캐시 key에 ALL role을 사용한다")
    void gapAnalysisCacheKeyUsesAllWhenTargetRolesAreEmpty() {
        assertThat(GapAnalysisService.gapAnalysisCacheKey(4L, 2L, null, 20))
                .isEqualTo("userId=4:projectId=2:roles=ALL:limit=20");

        assertThat(GapAnalysisService.gapAnalysisCacheKey(4L, 2L, List.of(), 20))
                .isEqualTo("userId=4:projectId=2:roles=ALL:limit=20");
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
