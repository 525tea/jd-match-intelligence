package jobflow.domain.project;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.project.dto.ProjectExperienceTagInventoryResponse;
import jobflow.domain.project.dto.ProjectSkillInventoryResponse;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.GlobalExceptionHandler;
import jobflow.global.error.exception.BusinessException;
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
        controllers = ProjectInventoryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ProjectInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectInventoryService projectInventoryService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("프로젝트 스킬 인벤토리 조회 성공 시 200 ApiResponse를 반환한다")
    void getProjectSkills() throws Exception {
        setAuthentication();
        List<ProjectSkillInventoryResponse> response = List.of(
                new ProjectSkillInventoryResponse(
                        10L,
                        100L,
                        2,
                        LocalDateTime.of(2026, 6, 15, 9, 0),
                        true,
                        1L,
                        "Java",
                        "java",
                        "LANGUAGE",
                        AnalysisSource.STATIC,
                        new BigDecimal("0.9500"),
                        "build.gradle implementation"
                ),
                new ProjectSkillInventoryResponse(
                        10L,
                        100L,
                        2,
                        LocalDateTime.of(2026, 6, 15, 9, 0),
                        true,
                        2L,
                        "Spring Boot",
                        "spring boot",
                        "FRAMEWORK",
                        AnalysisSource.STATIC,
                        new BigDecimal("0.9000"),
                        "build.gradle plugin"
                )
        );
        given(projectInventoryService.getProjectSkills(1L, 10L)).willReturn(response);

        mockMvc.perform(get("/projects/{userProjectId}/skills", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].userProjectId").value(10))
                .andExpect(jsonPath("$.data[0].analysisId").value(100))
                .andExpect(jsonPath("$.data[0].analysisVersion").value(2))
                .andExpect(jsonPath("$.data[0].latestAnalysis").value(true))
                .andExpect(jsonPath("$.data[0].skillId").value(1))
                .andExpect(jsonPath("$.data[0].skillName").value("Java"))
                .andExpect(jsonPath("$.data[0].normalizedName").value("java"))
                .andExpect(jsonPath("$.data[0].category").value("LANGUAGE"))
                .andExpect(jsonPath("$.data[0].source").value("STATIC"))
                .andExpect(jsonPath("$.data[0].confidence").value(0.9500))
                .andExpect(jsonPath("$.data[0].evidence").value("build.gradle implementation"))
                .andExpect(jsonPath("$.data[1].skillName").value("Spring Boot"));

        verify(projectInventoryService).getProjectSkills(1L, 10L);
    }

    @Test
    @DisplayName("프로젝트 경험 태그 인벤토리 조회 성공 시 200 ApiResponse를 반환한다")
    void getProjectExperienceTags() throws Exception {
        setAuthentication();
        List<ProjectExperienceTagInventoryResponse> response = List.of(
                new ProjectExperienceTagInventoryResponse(
                        10L,
                        100L,
                        2,
                        LocalDateTime.of(2026, 6, 15, 9, 0),
                        true,
                        "BACKEND_API",
                        "백엔드 API",
                        "REST API, 서버 개발 경험",
                        AnalysisSource.STATIC,
                        new BigDecimal("0.8800"),
                        "controller/service package"
                )
        );
        given(projectInventoryService.getProjectExperienceTags(1L, 10L)).willReturn(response);

        mockMvc.perform(get("/projects/{userProjectId}/experience-tags", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userProjectId").value(10))
                .andExpect(jsonPath("$.data[0].analysisId").value(100))
                .andExpect(jsonPath("$.data[0].analysisVersion").value(2))
                .andExpect(jsonPath("$.data[0].latestAnalysis").value(true))
                .andExpect(jsonPath("$.data[0].tagCode").value("BACKEND_API"))
                .andExpect(jsonPath("$.data[0].tagName").value("백엔드 API"))
                .andExpect(jsonPath("$.data[0].description").value("REST API, 서버 개발 경험"))
                .andExpect(jsonPath("$.data[0].source").value("STATIC"))
                .andExpect(jsonPath("$.data[0].confidence").value(0.8800))
                .andExpect(jsonPath("$.data[0].evidence").value("controller/service package"));

        verify(projectInventoryService).getProjectExperienceTags(1L, 10L);
    }

    @Test
    @DisplayName("프로젝트 스킬 인벤토리 대상 프로젝트가 없으면 404 ErrorResponse를 반환한다")
    void getProjectSkillsWithMissingProject() throws Exception {
        setAuthentication();
        willThrow(new BusinessException(
                ErrorCode.USER_PROJECT_NOT_FOUND,
                "사용자 프로젝트를 찾을 수 없습니다."
        )).given(projectInventoryService).getProjectSkills(1L, 999L);

        mockMvc.perform(get("/projects/{userProjectId}/skills", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("사용자 프로젝트를 찾을 수 없습니다."));

        verify(projectInventoryService).getProjectSkills(1L, 999L);
    }

    @Test
    @DisplayName("프로젝트 경험 태그 인벤토리 대상 프로젝트가 없으면 404 ErrorResponse를 반환한다")
    void getProjectExperienceTagsWithMissingProject() throws Exception {
        setAuthentication();
        willThrow(new BusinessException(
                ErrorCode.USER_PROJECT_NOT_FOUND,
                "사용자 프로젝트를 찾을 수 없습니다."
        )).given(projectInventoryService).getProjectExperienceTags(1L, 999L);

        mockMvc.perform(get("/projects/{userProjectId}/experience-tags", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_PROJECT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("사용자 프로젝트를 찾을 수 없습니다."));

        verify(projectInventoryService).getProjectExperienceTags(1L, 999L);
    }

    private void setAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(testAuthentication());
    }

    private Authentication testAuthentication() {
        UserPrincipal principal = new UserPrincipal(
                1L,
                "user@example.com",
                "테스트 사용자",
                "USER"
        );

        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
        );
    }
}
