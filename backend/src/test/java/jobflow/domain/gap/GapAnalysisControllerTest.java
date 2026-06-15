package jobflow.domain.gap;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapAnalysisResponse;
import jobflow.domain.gap.dto.GapJobMatchEvidenceResponse;
import jobflow.domain.gap.dto.GapJobMatchResponse;
import jobflow.domain.gap.dto.GapLearningConnectionResponse;
import jobflow.domain.gap.dto.GapRelatedTagEvidenceResponse;
import jobflow.domain.gap.dto.GapSkillCooccurrenceEvidenceResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.EntityNotFoundException;
import jobflow.global.security.JwtAuthenticationFilter;
import jobflow.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = GapAnalysisController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GapAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GapAnalysisService gapAnalysisService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로젝트 갭 분석 조회 성공 시 evidence를 포함한 200 ApiResponse를 반환한다")
    void analyzeProjectSkillGap() throws Exception {
        setAuthentication();
        GapAnalysisResponse response = gapAnalysisResponse();
        given(gapAnalysisService.analyzeProjectSkillGap(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                5
        )).willReturn(response);

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 10L)
                        .param("targetRoles", "BACKEND", "FULLSTACK")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userProjectId").value(10))
                .andExpect(jsonPath("$.data.userSkillIds", hasSize(3)))
                .andExpect(jsonPath("$.data.userSkillIds[0]").value(1))
                .andExpect(jsonPath("$.data.jobMatches", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].jobId").value(100))
                .andExpect(jsonPath("$.data.jobMatches[0].role").value("BACKEND"))
                .andExpect(jsonPath("$.data.jobMatches[0].requiredSkillCount").value(3))
                .andExpect(jsonPath("$.data.jobMatches[0].matchedRequiredSkillCount").value(2))
                .andExpect(jsonPath("$.data.jobMatches[0].missingRequiredSkillCount").value(1))
                .andExpect(jsonPath("$.data.jobMatches[0].requiredMatchRate").value(66.67))
                .andExpect(jsonPath("$.data.jobMatches[0].preferredMatchRate").value(50.0))
                .andExpect(jsonPath("$.data.jobMatches[0].matchScore").value(56.33))
                .andExpect(jsonPath("$.data.jobMatches[0].matchedRequiredSkills", hasSize(2)))
                .andExpect(jsonPath("$.data.jobMatches[0].matchedRequiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.jobMatches[0].missingRequiredSkills", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].missingRequiredSkills[0]").value("Kubernetes"))
                .andExpect(jsonPath("$.data.jobMatches[0].matchedPreferredSkills", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].matchedPreferredSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.data.jobMatches[0].missingPreferredSkills", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].missingPreferredSkills[0]").value("Kafka"))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.addedJobs").value(43))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.cooccurrences", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.cooccurrences[0].baseSkillName")
                        .value("Kubernetes"))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.cooccurrences[0].relatedSkillName")
                        .value("Docker"))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.relatedTags", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.relatedTags[0].tagCode")
                        .value("CLOUD_INFRA"))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.learningConnections", hasSize(1)))
                .andExpect(jsonPath("$.data.jobMatches[0].evidence.learningConnections[0].missingSkillName")
                        .value("Kubernetes"));

        verify(gapAnalysisService).analyzeProjectSkillGap(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                5
        );
    }

    @Test
    @DisplayName("targetRoles와 limit을 생략하면 전체 role 대상 기본 limit 20으로 조회한다")
    void analyzeProjectSkillGapWithDefaultQueryParams() throws Exception {
        setAuthentication();
        GapAnalysisResponse response = gapAnalysisResponse();
        given(gapAnalysisService.analyzeProjectSkillGap(
                1L,
                10L,
                null,
                20
        )).willReturn(response);

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userProjectId").value(10))
                .andExpect(jsonPath("$.data.jobMatches", hasSize(1)));

        verify(gapAnalysisService).analyzeProjectSkillGap(
                1L,
                10L,
                null,
                20
        );
    }

    @Test
    @DisplayName("필수 또는 우대 스킬 bucket이 비어 있으면 match rate를 null로 반환한다")
    void analyzeProjectSkillGapWithEmptyRequirementBucket() throws Exception {
        setAuthentication();
        GapAnalysisResponse response = new GapAnalysisResponse(
                10L,
                List.of(1L, 2L, 3L),
                List.of(
                        GapJobMatchResponse.from(
                                jobSkillMatchResponseWithEmptyRequiredBucket(),
                                GapJobMatchEvidenceResponse.empty()
                        ),
                        GapJobMatchResponse.from(
                                jobSkillMatchResponseWithEmptyPreferredBucket(),
                                GapJobMatchEvidenceResponse.empty()
                        )
                )
        );
        given(gapAnalysisService.analyzeProjectSkillGap(
                1L,
                10L,
                List.of(JobRole.BACKEND),
                5
        )).willReturn(response);

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 10L)
                        .param("targetRoles", "BACKEND")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobMatches", hasSize(2)))
                .andExpect(jsonPath("$.data.jobMatches[0].requiredSkillCount").value(0))
                .andExpect(jsonPath("$.data.jobMatches[0].requiredMatchRate").value(nullValue()))
                .andExpect(jsonPath("$.data.jobMatches[0].preferredSkillCount").value(1))
                .andExpect(jsonPath("$.data.jobMatches[0].preferredMatchRate").value(100.00))
                .andExpect(jsonPath("$.data.jobMatches[1].requiredSkillCount").value(1))
                .andExpect(jsonPath("$.data.jobMatches[1].requiredMatchRate").value(100.00))
                .andExpect(jsonPath("$.data.jobMatches[1].preferredSkillCount").value(0))
                .andExpect(jsonPath("$.data.jobMatches[1].preferredMatchRate").value(nullValue()));

        verify(gapAnalysisService).analyzeProjectSkillGap(
                1L,
                10L,
                List.of(JobRole.BACKEND),
                5
        );
    }

    @Test
    @DisplayName("프로젝트 갭 분석 대상 프로젝트가 없으면 404 ErrorResponse를 반환한다")
    void analyzeProjectSkillGapWithMissingProject() throws Exception {
        setAuthentication();
        willThrow(new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND))
                .given(gapAnalysisService)
                .analyzeProjectSkillGap(1L, 999L, List.of(JobRole.BACKEND), 5);

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 999L)
                        .param("targetRoles", "BACKEND")
                        .param("limit", "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("사용자 프로젝트를 찾을 수 없습니다."));

        verify(gapAnalysisService).analyzeProjectSkillGap(1L, 999L, List.of(JobRole.BACKEND), 5);
    }

    @Test
    @DisplayName("프로젝트 갭 분석 targetRoles가 존재하지 않는 role이면 400 ErrorResponse를 반환한다")
    void analyzeProjectSkillGapWithInvalidTargetRole() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 10L)
                        .param("targetRoles", "NOT_A_ROLE")
                        .param("limit", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("요청 파라미터 타입이 올바르지 않습니다."));

        verifyNoInteractions(gapAnalysisService);
    }

    @Test
    @DisplayName("프로젝트 갭 분석 limit이 1보다 작으면 400 ErrorResponse를 반환한다")
    void analyzeProjectSkillGapWithTooSmallLimit() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 10L)
                        .param("targetRoles", "BACKEND")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("limit은 1 이상 50 이하로 요청해야 합니다."));

        verifyNoInteractions(gapAnalysisService);
    }

    @Test
    @DisplayName("프로젝트 갭 분석 limit이 50보다 크면 400 ErrorResponse를 반환한다")
    void analyzeProjectSkillGapWithTooLargeLimit() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/gap-analysis/projects/{userProjectId}", 10L)
                        .param("targetRoles", "BACKEND")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("limit은 1 이상 50 이하로 요청해야 합니다."));

        verifyNoInteractions(gapAnalysisService);
    }

    private void setAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(testAuthentication());
    }

    private Authentication testAuthentication() {
        UserPrincipal principal = new UserPrincipal(
                1L,
                "test@example.com",
                "테스트",
                "USER"
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );
    }

    private GapAnalysisResponse gapAnalysisResponse() {
        return new GapAnalysisResponse(
                10L,
                List.of(1L, 2L, 3L),
                List.of(GapJobMatchResponse.from(jobSkillMatchResponse(), gapJobMatchEvidenceResponse()))
        );
    }

    private GapJobMatchEvidenceResponse gapJobMatchEvidenceResponse() {
        return new GapJobMatchEvidenceResponse(
                43,
                List.of(new GapSkillCooccurrenceEvidenceResponse(
                        "Kubernetes",
                        "Docker",
                        12,
                        43,
                        61,
                        new BigDecimal("2.5000")
                )),
                List.of(new GapRelatedTagEvidenceResponse(
                        "Kubernetes",
                        "CLOUD_INFRA",
                        "클라우드/인프라",
                        "클라우드 인프라 경험",
                        20,
                        43,
                        120,
                        new BigDecimal("1.7000")
                )),
                List.of(new GapLearningConnectionResponse(
                        "Kubernetes",
                        "Kubernetes은(는) Docker와 함께 자주 등장하고, 클라우드/인프라 경험과 연결됩니다."
                ))
        );
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

    private JobSkillMatchResponse jobSkillMatchResponseWithEmptyRequiredBucket() {
        return new JobSkillMatchResponse(
                101L,
                "Redis 우대 백엔드 개발자",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                0,
                0,
                0,
                null,
                1,
                1,
                0,
                BigDecimal.valueOf(100.00),
                BigDecimal.valueOf(13.00),
                List.of(),
                List.of(),
                List.of("Redis"),
                List.of()
        );
    }

    private JobSkillMatchResponse jobSkillMatchResponseWithEmptyPreferredBucket() {
        return new JobSkillMatchResponse(
                102L,
                "Java 필수 백엔드 개발자",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                1,
                1,
                0,
                BigDecimal.valueOf(100.00),
                0,
                0,
                0,
                null,
                BigDecimal.valueOf(60.00),
                List.of("Java"),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
