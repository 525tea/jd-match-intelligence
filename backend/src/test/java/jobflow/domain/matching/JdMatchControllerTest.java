package jobflow.domain.matching;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.matching.dto.JdJobMatchResponse;
import jobflow.domain.matching.dto.JdMatchExperienceTagResponse;
import jobflow.domain.matching.dto.JdMatchScoreResponse;
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
        controllers = JdMatchController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class JdMatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdMatchService jdMatchService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로젝트 JD 매칭 조회 성공 시 200 ApiResponse를 반환한다")
    void findProjectJobMatches() throws Exception {
        setAuthentication();
        List<JdJobMatchResponse> response = List.of(jdJobMatchResponse());
        given(jdMatchService.findProjectJobMatches(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                CareerLevel.MID,
                5
        )).willReturn(response);

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 10L)
                        .param("targetRoles", "BACKEND", "FULLSTACK")
                        .param("targetCareerLevel", "MID")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userProjectId").value(10))
                .andExpect(jsonPath("$.data[0].analysisId").value(100))
                .andExpect(jsonPath("$.data[0].analysisVersion").value(2))
                .andExpect(jsonPath("$.data[0].jobId").value(200))
                .andExpect(jsonPath("$.data[0].title").value("백엔드 플랫폼 개발자"))
                .andExpect(jsonPath("$.data[0].companyName").value("Example Company"))
                .andExpect(jsonPath("$.data[0].role").value("BACKEND"))
                .andExpect(jsonPath("$.data[0].careerLevel").value("MID"))
                .andExpect(jsonPath("$.data[0].score.totalScore").value(82.50))
                .andExpect(jsonPath("$.data[0].score.requiredSkillScore").value(33.75))
                .andExpect(jsonPath("$.data[0].score.preferredSkillScore").value(10.00))
                .andExpect(jsonPath("$.data[0].score.experienceTagScore").value(20.00))
                .andExpect(jsonPath("$.data[0].score.careerLevelScore").value(10.00))
                .andExpect(jsonPath("$.data[0].score.confidenceScore").value(8.75))
                .andExpect(jsonPath("$.data[0].requiredSkillCount").value(4))
                .andExpect(jsonPath("$.data[0].matchedRequiredSkillCount").value(3))
                .andExpect(jsonPath("$.data[0].missingRequiredSkillCount").value(1))
                .andExpect(jsonPath("$.data[0].preferredSkillCount").value(2))
                .andExpect(jsonPath("$.data[0].matchedPreferredSkillCount").value(1))
                .andExpect(jsonPath("$.data[0].missingPreferredSkillCount").value(1))
                .andExpect(jsonPath("$.data[0].experienceTagCount").value(2))
                .andExpect(jsonPath("$.data[0].matchedExperienceTagCount").value(2))
                .andExpect(jsonPath("$.data[0].missingExperienceTagCount").value(0))
                .andExpect(jsonPath("$.data[0].matchedRequiredSkills", hasSize(3)))
                .andExpect(jsonPath("$.data[0].matchedRequiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data[0].missingRequiredSkills[0]").value("Kubernetes"))
                .andExpect(jsonPath("$.data[0].matchedPreferredSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.data[0].missingPreferredSkills[0]").value("Kafka"))
                .andExpect(jsonPath("$.data[0].matchedExperienceTags", hasSize(2)))
                .andExpect(jsonPath("$.data[0].matchedExperienceTags[0].code").value("CI_CD"))
                .andExpect(jsonPath("$.data[0].matchedExperienceTags[0].name").value("CI/CD"))
                .andExpect(jsonPath("$.data[0].matchedExperienceTags[0].description").value("CI/CD 경험"))
                .andExpect(jsonPath("$.data[0].missingExperienceTags", hasSize(0)));

        verify(jdMatchService).findProjectJobMatches(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                CareerLevel.MID,
                5
        );
    }

    @Test
    @DisplayName("targetRoles, targetCareerLevel, limit을 생략하면 전체 role 대상 기본 limit 20으로 조회한다")
    void findProjectJobMatchesWithDefaultQueryParams() throws Exception {
        setAuthentication();
        List<JdJobMatchResponse> response = List.of(jdJobMatchResponse());
        given(jdMatchService.findProjectJobMatches(
                1L,
                10L,
                null,
                null,
                20
        )).willReturn(response);

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(jdMatchService).findProjectJobMatches(
                1L,
                10L,
                null,
                null,
                20
        );
    }

    @Test
    @DisplayName("프로젝트 JD 매칭 대상 프로젝트가 없으면 404 ErrorResponse를 반환한다")
    void findProjectJobMatchesWithMissingProject() throws Exception {
        setAuthentication();
        willThrow(new EntityNotFoundException(ErrorCode.USER_PROJECT_NOT_FOUND))
                .given(jdMatchService)
                .findProjectJobMatches(1L, 999L, List.of(JobRole.BACKEND), CareerLevel.MID, 5);

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 999L)
                        .param("targetRoles", "BACKEND")
                        .param("targetCareerLevel", "MID")
                        .param("limit", "5"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("사용자 프로젝트를 찾을 수 없습니다."));

        verify(jdMatchService).findProjectJobMatches(1L, 999L, List.of(JobRole.BACKEND), CareerLevel.MID, 5);
    }

    @Test
    @DisplayName("프로젝트 JD 매칭 targetRoles가 존재하지 않는 role이면 400 ErrorResponse를 반환한다")
    void findProjectJobMatchesWithInvalidTargetRole() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 10L)
                        .param("targetRoles", "NOT_A_ROLE")
                        .param("limit", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("요청 파라미터 타입이 올바르지 않습니다."));

        verifyNoInteractions(jdMatchService);
    }

    @Test
    @DisplayName("프로젝트 JD 매칭 targetCareerLevel이 존재하지 않는 값이면 400 ErrorResponse를 반환한다")
    void findProjectJobMatchesWithInvalidTargetCareerLevel() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 10L)
                        .param("targetCareerLevel", "NOT_A_LEVEL")
                        .param("limit", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("요청 파라미터 타입이 올바르지 않습니다."));

        verifyNoInteractions(jdMatchService);
    }

    @Test
    @DisplayName("프로젝트 JD 매칭 limit이 1보다 작으면 400 ErrorResponse를 반환한다")
    void findProjectJobMatchesWithTooSmallLimit() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 10L)
                        .param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("limit은 1 이상 50 이하로 요청해야 합니다."));

        verifyNoInteractions(jdMatchService);
    }

    @Test
    @DisplayName("프로젝트 JD 매칭 limit이 50보다 크면 400 ErrorResponse를 반환한다")
    void findProjectJobMatchesWithTooLargeLimit() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/projects/{userProjectId}/job-matches", 10L)
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("limit은 1 이상 50 이하로 요청해야 합니다."));

        verifyNoInteractions(jdMatchService);
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

    private JdJobMatchResponse jdJobMatchResponse() {
        return new JdJobMatchResponse(
                10L,
                100L,
                2,
                LocalDateTime.of(2026, 6, 15, 9, 0),
                200L,
                "백엔드 플랫폼 개발자",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.MID,
                jdMatchScoreResponse(),
                4,
                3,
                1,
                2,
                1,
                1,
                2,
                2,
                0,
                List.of("Java", "Spring Boot", "MySQL"),
                List.of("Kubernetes"),
                List.of("Docker"),
                List.of("Kafka"),
                List.of(
                        new JdMatchExperienceTagResponse("CI_CD", "CI/CD", "CI/CD 경험"),
                        new JdMatchExperienceTagResponse("CLOUD_INFRA", "클라우드/인프라", "클라우드 인프라 경험")
                ),
                List.of()
        );
    }

    private JdMatchScoreResponse jdMatchScoreResponse() {
        return new JdMatchScoreResponse(
                new BigDecimal("82.50"),
                new BigDecimal("33.75"),
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                new BigDecimal("8.75"),
                new BigDecimal("0.75"),
                new BigDecimal("0.50"),
                new BigDecimal("1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("0.8750")
        );
    }
}
