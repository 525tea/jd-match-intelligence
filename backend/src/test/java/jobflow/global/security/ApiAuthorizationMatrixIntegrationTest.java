package jobflow.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import jobflow.domain.analytics.AnalyticsTrendService;
import jobflow.domain.job.JobService;
import jobflow.domain.outbox.DlqMessageService;
import jobflow.domain.outbox.DlqRetryRequest;
import jobflow.domain.outbox.DlqRetryResponse;
import jobflow.domain.outbox.KafkaDlqRetryService;
import jobflow.domain.skill.SkillCategory;
import jobflow.domain.skill.SkillService;
import jobflow.domain.skill.dto.SkillCreateRequest;
import jobflow.domain.skill.dto.SkillResponse;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.domain.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiAuthorizationMatrixIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SkillService skillService;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private AnalyticsTrendService analyticsTrendService;

    @MockitoBean
    private KafkaDlqRetryService kafkaDlqRetryService;

    @MockitoBean
    private DlqMessageService dlqMessageService;

    @Test
    @DisplayName("PUBLIC API는 토큰 없이 호출할 수 있다")
    void publicApisAllowAnonymousAccess() throws Exception {
        given(skillService.findSkills(null, null)).willReturn(List.of());
        given(jobService.getJobs(any())).willReturn(List.of());
        given(jobService.searchJobs("backend", 3)).willReturn(List.of());
        given(analyticsTrendService.getSkillTrends(null, 3)).willReturn(List.of());

        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/jobs/search")
                        .param("keyword", "backend")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/trends/skills")
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("USER API는 토큰 없이는 401, USER 토큰이면 통과한다")
    void userApisRequireAuthenticatedUser() throws Exception {
        User user = saveUser("matrix-user@example.com", UserRole.USER);
        String accessToken = jwtTokenProvider.createAccessToken(user);

        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/auth/me"));

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("matrix-user@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("ADMIN API는 토큰 없이는 401, USER 토큰이면 403, ADMIN 토큰이면 통과한다")
    void adminApisRequireAdminRole() throws Exception {
        User user = saveUser("matrix-normal-user@example.com", UserRole.USER);
        User admin = saveUser("matrix-admin@example.com", UserRole.ADMIN);

        String userToken = jwtTokenProvider.createAccessToken(user);
        String adminToken = jwtTokenProvider.createAccessToken(admin);

        String requestBody = """
                {
                  "name": "Matrix Skill",
                  "normalizedName": "matrix skill",
                  "category": "FRAMEWORK"
                }
                """;

        given(skillService.createSkill(any(SkillCreateRequest.class)))
                .willReturn(new SkillResponse(
                        1L,
                        "Matrix Skill",
                        "matrix skill",
                        SkillCategory.FRAMEWORK
                ));

        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/skills"));

        mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/skills"));

        mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Matrix Skill"));
    }

    @Test
    @DisplayName("공고 관리 API도 ADMIN 권한으로 보호된다")
    void jobManagementApisRequireAdminRole() throws Exception {
        User user = saveUser("matrix-job-user@example.com", UserRole.USER);
        User admin = saveUser("matrix-job-admin@example.com", UserRole.ADMIN);

        String userToken = jwtTokenProvider.createAccessToken(user);
        String adminToken = jwtTokenProvider.createAccessToken(admin);

        mockMvc.perform(patch("/jobs/{jobId}/close", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/jobs/1/close"));

        mockMvc.perform(patch("/jobs/{jobId}/close", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/jobs/1/close"));

        given(jobService.closeJob(1L)).willReturn(null);

        mockMvc.perform(patch("/jobs/{jobId}/close", 1L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DLQ 재처리 API도 ADMIN 권한으로 보호된다")
    void dlqRetryApiRequiresAdminRole() throws Exception {
        User user = saveUser("matrix-dlq-user@example.com", UserRole.USER);
        User admin = saveUser("matrix-dlq-admin@example.com", UserRole.ADMIN);

        String userToken = jwtTokenProvider.createAccessToken(user);
        String adminToken = jwtTokenProvider.createAccessToken(admin);
        String requestBody = """
                {
                  "schemaVersion": 1,
                  "sourceTopic": "job.created",
                  "sourceKey": "JOB:1",
                  "original": {
                    "payload": {
                      "jobId": 1
                    }
                  }
                }
                """;

        mockMvc.perform(post("/admin/dlq/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"));

        mockMvc.perform(post("/admin/dlq/retry")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));

        given(kafkaDlqRetryService.retry(any(DlqRetryRequest.class)))
                .willReturn(new DlqRetryResponse(1, "job.created", "JOB:1"));

        mockMvc.perform(post("/admin/dlq/retry")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetTopic").value("job.created"));
    }

    private User saveUser(String email, UserRole role) {
        User user = User.signup(
                email,
                "encoded-password",
                "테스트 사용자"
        );
        ReflectionTestUtils.setField(user, "role", role);

        return userRepository.save(user);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
