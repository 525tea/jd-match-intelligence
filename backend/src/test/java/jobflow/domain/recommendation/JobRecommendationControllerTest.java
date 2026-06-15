package jobflow.domain.recommendation;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.EmploymentType;
import jobflow.domain.job.JobRole;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.RemoteType;
import jobflow.domain.recommendation.dto.JobRecommendationResponse;
import jobflow.domain.recommendation.dto.JobRecommendationScoreResponse;
import jobflow.domain.userjob.UserJobStatus;
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
        controllers = JobRecommendationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class JobRecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobRecommendationService jobRecommendationService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("공고 추천 조회 성공 시 200 ApiResponse를 반환한다")
    void recommendJobs() throws Exception {
        setAuthentication();
        List<JobRecommendationResponse> response = List.of(jobRecommendationResponse());
        given(jobRecommendationService.recommendJobs(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                5
        )).willReturn(response);

        mockMvc.perform(get("/recommendations/jobs")
                        .param("userProjectId", "10")
                        .param("targetRoles", "BACKEND", "FULLSTACK")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].jobId").value(100))
                .andExpect(jsonPath("$.data[0].source").value("TEST_SOURCE"))
                .andExpect(jsonPath("$.data[0].title").value("Sample backend engineer"))
                .andExpect(jsonPath("$.data[0].companyName").value("Example Company"))
                .andExpect(jsonPath("$.data[0].role").value("BACKEND"))
                .andExpect(jsonPath("$.data[0].careerLevel").value("MID"))
                .andExpect(jsonPath("$.data[0].employmentType").value("FULL_TIME"))
                .andExpect(jsonPath("$.data[0].locationRegion").value("Seoul"))
                .andExpect(jsonPath("$.data[0].locationCity").value("Gangnam"))
                .andExpect(jsonPath("$.data[0].remoteType").value("ONSITE"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].userJobStatus").value("SAVED"))
                .andExpect(jsonPath("$.data[0].score.totalScore").value(83.50))
                .andExpect(jsonPath("$.data[0].score.skillMatchScore").value(36.00))
                .andExpect(jsonPath("$.data[0].score.freshnessScore").value(17.50))
                .andExpect(jsonPath("$.data[0].score.behaviorScore").value(25.00))
                .andExpect(jsonPath("$.data[0].score.popularityScore").value(5.00))
                .andExpect(jsonPath("$.data[0].requiredSkillCount").value(3))
                .andExpect(jsonPath("$.data[0].matchedRequiredSkillCount").value(2))
                .andExpect(jsonPath("$.data[0].missingRequiredSkillCount").value(1))
                .andExpect(jsonPath("$.data[0].preferredSkillCount").value(2))
                .andExpect(jsonPath("$.data[0].matchedPreferredSkillCount").value(1))
                .andExpect(jsonPath("$.data[0].missingPreferredSkillCount").value(1))
                .andExpect(jsonPath("$.data[0].matchedRequiredSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data[0].missingRequiredSkills[0]").value("Kotlin"))
                .andExpect(jsonPath("$.data[0].matchedPreferredSkills[0]").value("Docker"))
                .andExpect(jsonPath("$.data[0].missingPreferredSkills[0]").value("Redis"));

        verify(jobRecommendationService).recommendJobs(
                1L,
                10L,
                List.of(JobRole.BACKEND, JobRole.FULLSTACK),
                5
        );
    }

    @Test
    @DisplayName("targetRoles, limit을 생략하면 전체 role 대상 기본 limit 20으로 조회한다")
    void recommendJobsWithDefaultQueryParams() throws Exception {
        setAuthentication();
        List<JobRecommendationResponse> response = List.of(jobRecommendationResponse());
        given(jobRecommendationService.recommendJobs(1L, 10L, null, 20)).willReturn(response);

        mockMvc.perform(get("/recommendations/jobs")
                        .param("userProjectId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)));

        verify(jobRecommendationService).recommendJobs(1L, 10L, null, 20);
    }

    @Test
    @DisplayName("limit이 허용 범위를 벗어나면 400을 반환한다")
    void recommendJobsWithInvalidLimit() throws Exception {
        setAuthentication();

        mockMvc.perform(get("/recommendations/jobs")
                        .param("userProjectId", "10")
                        .param("limit", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));

        verifyNoInteractions(jobRecommendationService);
    }

    private void setAuthentication() {
        UserPrincipal principal = new UserPrincipal(
                1L,
                "user@example.com",
                "Example User",
                "USER"
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private JobRecommendationResponse jobRecommendationResponse() {
        return new JobRecommendationResponse(
                100L,
                "TEST_SOURCE",
                "Sample backend engineer",
                "Example Company",
                JobRole.BACKEND,
                CareerLevel.MID,
                EmploymentType.FULL_TIME,
                "Seoul",
                "Gangnam",
                RemoteType.ONSITE,
                LocalDateTime.of(2026, 7, 1, 23, 59),
                JobStatus.OPEN,
                UserJobStatus.SAVED,
                new JobRecommendationScoreResponse(
                        new BigDecimal("83.50"),
                        new BigDecimal("36.00"),
                        new BigDecimal("17.50"),
                        new BigDecimal("25.00"),
                        new BigDecimal("5.00"),
                        new BigDecimal("90.00"),
                        new BigDecimal("87.50"),
                        new BigDecimal("83.33"),
                        new BigDecimal("50.00")
                ),
                3,
                2,
                1,
                2,
                1,
                1,
                List.of("Java", "Spring Boot"),
                List.of("Kotlin"),
                List.of("Docker"),
                List.of("Redis")
        );
    }
}
