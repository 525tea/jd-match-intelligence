package jobflow.domain.analytics;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import jobflow.domain.analytics.dto.JobMarketStatsResponse;
import jobflow.domain.analytics.dto.SkillCooccurrenceResponse;
import jobflow.domain.analytics.dto.SkillExperienceMarketResponse;
import jobflow.domain.analytics.dto.SkillTrendResponse;
import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import jobflow.domain.skill.SkillCategory;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest(
        controllers = AnalyticsTrendController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AnalyticsTrendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalyticsTrendService analyticsTrendService;

    @Test
    @DisplayName("스킬 트렌드 조회 성공 시 200 ApiResponse를 반환한다")
    void getSkillTrends() throws Exception {
        LocalDate month = LocalDate.of(2026, 6, 1);
        given(analyticsTrendService.getSkillTrends(month, 10))
                .willReturn(List.of(skillTrendResponse()));

        mockMvc.perform(get("/trends/skills")
                        .param("month", "2026-06-01")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].skillId").value(1))
                .andExpect(jsonPath("$.data[0].skillName").value("Spring Boot"))
                .andExpect(jsonPath("$.data[0].skillCategory").value("FRAMEWORK"))
                .andExpect(jsonPath("$.data[0].jobCount").value(12))
                .andExpect(jsonPath("$.data[0].trendScore").value(21.5));
    }

    @Test
    @DisplayName("스킬 동시 등장 조회 성공 시 200 ApiResponse를 반환한다")
    void getSkillCooccurrences() throws Exception {
        LocalDate month = LocalDate.of(2026, 6, 1);
        given(analyticsTrendService.getSkillCooccurrences(month, 1L, 10))
                .willReturn(List.of(skillCooccurrenceResponse()));

        mockMvc.perform(get("/trends/skills/{skillId}/cooccurrences", 1L)
                        .param("month", "2026-06-01")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].baseSkillName").value("Spring Boot"))
                .andExpect(jsonPath("$.data[0].coSkillName").value("Redis"))
                .andExpect(jsonPath("$.data[0].cooccurrenceCount").value(6))
                .andExpect(jsonPath("$.data[0].liftScore").value(1.7));
    }

    @Test
    @DisplayName("스킬-경험 태그 시장 조회 성공 시 200 ApiResponse를 반환한다")
    void getSkillExperienceMarkets() throws Exception {
        LocalDate month = LocalDate.of(2026, 6, 1);
        given(analyticsTrendService.getSkillExperienceMarkets(month, 1L, 10))
                .willReturn(List.of(skillExperienceMarketResponse()));

        mockMvc.perform(get("/trends/skills/{skillId}/experience-tags", 1L)
                        .param("month", "2026-06-01")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].skillName").value("Spring Boot"))
                .andExpect(jsonPath("$.data[0].tagCode").value("HIGH_TRAFFIC"))
                .andExpect(jsonPath("$.data[0].tagName").value("대용량 트래픽"))
                .andExpect(jsonPath("$.data[0].jobCount").value(5))
                .andExpect(jsonPath("$.data[0].liftScore").value(2.1));
    }

    @Test
    @DisplayName("공고 시장 통계 조회 성공 시 200 ApiResponse를 반환한다")
    void getJobMarketStats() throws Exception {
        LocalDate month = LocalDate.of(2026, 6, 1);
        given(analyticsTrendService.getJobMarketStats(month, JobRole.BACKEND, 10))
                .willReturn(List.of(jobMarketStatsResponse()));

        mockMvc.perform(get("/trends/market")
                        .param("month", "2026-06-01")
                        .param("role", "BACKEND")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].role").value("BACKEND"))
                .andExpect(jsonPath("$.data[0].careerLevel").value("JUNIOR"))
                .andExpect(jsonPath("$.data[0].locationRegion").value("Seoul"))
                .andExpect(jsonPath("$.data[0].remoteType").value("HYBRID"))
                .andExpect(jsonPath("$.data[0].jobCount").value(12));
    }

    private SkillTrendResponse skillTrendResponse() {
        return new SkillTrendResponse(
                1L,
                "Spring Boot",
                SkillCategory.FRAMEWORK,
                LocalDate.of(2026, 6, 1),
                12,
                8,
                4,
                BigDecimal.valueOf(21.5)
        );
    }

    private SkillCooccurrenceResponse skillCooccurrenceResponse() {
        return new SkillCooccurrenceResponse(
                1L,
                "Spring Boot",
                2L,
                "Redis",
                LocalDate.of(2026, 6, 1),
                6,
                12,
                8,
                BigDecimal.valueOf(1.7)
        );
    }

    private SkillExperienceMarketResponse skillExperienceMarketResponse() {
        return new SkillExperienceMarketResponse(
                1L,
                "Spring Boot",
                "HIGH_TRAFFIC",
                "대용량 트래픽",
                LocalDate.of(2026, 6, 1),
                5,
                12,
                7,
                BigDecimal.valueOf(2.1)
        );
    }

    private JobMarketStatsResponse jobMarketStatsResponse() {
        return new JobMarketStatsResponse(
                JobRole.BACKEND,
                CareerLevel.JUNIOR,
                "Seoul",
                "HYBRID",
                LocalDate.of(2026, 6, 1),
                12,
                10,
                1,
                1,
                BigDecimal.valueOf(1.5),
                BigDecimal.valueOf(4.0)
        );
    }
}
