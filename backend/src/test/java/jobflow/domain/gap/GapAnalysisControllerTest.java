package jobflow.domain.gap;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import jobflow.domain.analytics.dto.JobSkillMatchResponse;
import jobflow.domain.gap.dto.GapAnalysisResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.global.error.GlobalExceptionHandler;
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
    @DisplayName("프로젝트 갭 분석 조회 성공 시 200 ApiResponse를 반환한다")
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
                .andExpect(jsonPath("$.data.jobMatches[0].missingPreferredSkills[0]").value("Kafka"));

        verify(gapAnalysisService).analyzeProjectSkillGap(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                5
        );
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
                List.of(jobSkillMatchResponse())
        );
    }

    private JobSkillMatchResponse jobSkillMatchResponse() {
        return new JobSkillMatchResponse(
                100L,
                "백엔드 개발자",
                "JobFlow",
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
