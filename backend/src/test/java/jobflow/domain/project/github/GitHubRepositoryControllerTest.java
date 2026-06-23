package jobflow.domain.project.github;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = GitHubRepositoryController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GitHubRepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubRepositoryAnalysisService gitHubRepositoryAnalysisService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GitHub repository 목록 조회 성공 시 200 ApiResponse를 반환한다")
    void getRepositories() throws Exception {
        setAuthentication();
        given(gitHubRepositoryAnalysisService.listRepositories(1L))
                .willReturn(List.of(new GitHubRepositoryResponse(
                        "example-org",
                        "sample-repo",
                        "example-org/sample-repo",
                        "main",
                        false,
                        "https://github.example/example-org/sample-repo",
                        "sample repository",
                        Instant.parse("2026-06-23T01:02:03Z")
                )));

        mockMvc.perform(get("/github/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].owner").value("example-org"))
                .andExpect(jsonPath("$.data[0].name").value("sample-repo"))
                .andExpect(jsonPath("$.data[0].fullName").value("example-org/sample-repo"))
                .andExpect(jsonPath("$.data[0].defaultBranch").value("main"));

        verify(gitHubRepositoryAnalysisService).listRepositories(1L);
    }

    @Test
    @DisplayName("인증 principal 없이 GitHub repository 목록을 조회하면 401을 반환한다")
    void getRepositoriesWithoutPrincipal() throws Exception {
        mockMvc.perform(get("/github/repositories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GitHub repository import 성공 시 분석 결과를 반환한다")
    void importRepository() throws Exception {
        setAuthentication();
        given(gitHubRepositoryAnalysisService.importRepository(any(Long.class), any(GitHubRepositoryImportRequest.class)))
                .willReturn(new GitHubRepositoryImportResponse(
                        10L,
                        "example-org/sample-repo",
                        "main",
                        100L,
                        2,
                        false,
                        3,
                        2,
                        List.of("Java", "Spring Boot"),
                        List.of("Next.js"),
                        2,
                        1,
                        List.of("CI_CD"),
                        List.of("UNKNOWN_TAG")
                ));

        mockMvc.perform(post("/projects/github-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "owner": "example-org",
                                  "name": "sample-repo",
                                  "ref": "main",
                                  "htmlUrl": "https://github.example/example-org/sample-repo",
                                  "description": "sample repository"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userProjectId").value(10))
                .andExpect(jsonPath("$.data.repositoryFullName").value("example-org/sample-repo"))
                .andExpect(jsonPath("$.data.analysisId").value(100))
                .andExpect(jsonPath("$.data.savedSkillNames[0]").value("Java"))
                .andExpect(jsonPath("$.data.savedTagCodes[0]").value("CI_CD"));

        verify(gitHubRepositoryAnalysisService)
                .importRepository(any(Long.class), any(GitHubRepositoryImportRequest.class));
    }

    @Test
    @DisplayName("인증 principal 없이 GitHub repository import를 요청하면 401을 반환한다")
    void importRepositoryWithoutPrincipal() throws Exception {
        mockMvc.perform(post("/projects/github-import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "owner": "example-org",
                                  "name": "sample-repo",
                                  "ref": "main"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"));
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
