package jobflow.global.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jobflow.domain.job.dto.JobCanonicalGroupItemResponse;
import jobflow.domain.job.dto.JobCanonicalGroupResponse;
import jobflow.domain.job.JobStatus;
import jobflow.domain.job.JobService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationIntegrationTest {

    private static final String TEST_JWT_SECRET = "jobflow-test-secret-key-must-be-at-least-32-bytes-long";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JobService jobService;

    @Test
    @DisplayName("인증 토큰 없이 보호 API 요청 시 401 공통 ErrorResponse를 반환한다")
    void requestProtectedApiWithoutToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("인증 토큰 없이 공고 검색 API를 호출할 수 있다")
    void searchJobsWithoutToken() throws Exception {
        given(jobService.searchJobs("백엔드", 10))
                .willReturn(List.of());

        mockMvc.perform(get("/jobs/search")
                        .param("keyword", "백엔드")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    @Test
    @DisplayName("인증 토큰 없이 canonical group API를 호출할 수 있다")
    void getCanonicalGroupWithoutToken() throws Exception {
        given(jobService.getCanonicalGroup(1L))
                .willReturn(new JobCanonicalGroupResponse(
                        "example-company|backend-engineer|seoul",
                        2L,
                        "https://company.example.com/jobs/backend",
                        1,
                        List.of(
                                new JobCanonicalGroupItemResponse(
                                        1L,
                                        "WANTED",
                                        "1001",
                                        "Backend Engineer",
                                        "Example Company",
                                        "https://www.wanted.co.kr/wd/1001",
                                        JobStatus.OPEN,
                                        null,
                                        false
                                ),
                                new JobCanonicalGroupItemResponse(
                                        2L,
                                        "JUMPIT",
                                        "2001",
                                        "Backend Engineer",
                                        "Example Company",
                                        "https://company.example.com/jobs/backend",
                                        JobStatus.OPEN,
                                        null,
                                        true
                                )
                        )
                ));

        mockMvc.perform(get("/jobs/{jobId}/canonical-group", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.representativeApplyUrl").value("https://company.example.com/jobs/backend"));
    }

    @Test
    @DisplayName("변조된 JWT로 보호 API 요청 시 401 공통 ErrorResponse를 반환한다")
    void requestProtectedApiWithTamperedToken() throws Exception {
        User user = saveLocalUser("tampered@example.com");
        String validToken = jwtTokenProvider.createAccessToken(user);
        String tamperedToken = validToken + "tampered";

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tamperedToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("만료된 JWT로 보호 API 요청 시 401 공통 ErrorResponse를 반환한다")
    void requestProtectedApiWithExpiredToken() throws Exception {
        User user = saveLocalUser("expired@example.com");
        String expiredToken = createExpiredToken(user);

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("JWT 인증은 사용자 DB 조회 없이 claim 기반 UserPrincipal을 만든다")
    void requestProtectedApiWithStatelessJwtClaims() throws Exception {
        User user = saveLocalUser("deleted-user-token@example.com");
        String accessToken = jwtTokenProvider.createAccessToken(user);

        userRepository.delete(user);
        userRepository.flush();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value("deleted-user-token@example.com"))
                .andExpect(jsonPath("$.data.name").value("테스트"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("기존 인증 객체가 있어도 JWT UserPrincipal로 보호 API를 인증한다")
    void requestProtectedApiWithExistingAuthenticationAndJwt() throws Exception {
        User user = saveLocalUser("oauth-jwt-user@example.com");
        String accessToken = jwtTokenProvider.createAccessToken(user);
        UsernamePasswordAuthenticationToken existingAuthentication =
                new UsernamePasswordAuthenticationToken("oauth-principal", null, List.of());

        mockMvc.perform(get("/auth/me")
                        .with(authentication(existingAuthentication))
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value("oauth-jwt-user@example.com"));
    }

    @Test
    @DisplayName("권한이 부족한 요청 시 403 공통 ErrorResponse를 반환한다")
    void requestAdminApiWithUserRole() throws Exception {
        User user = saveLocalUser("user-role@example.com");
        String accessToken = jwtTokenProvider.createAccessToken(user);

        String requestBody = """
                {
                  "name": "Spring Boot",
                  "normalizedName": "spring boot",
                  "category": "FRAMEWORK"
                }
                """;

        mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("접근 권한이 없습니다."));
    }

    @Test
    @DisplayName("ADMIN 권한이면 기준 데이터 생성 API를 호출할 수 있다")
    void requestAdminApiWithAdminRole() throws Exception {
        User admin = saveAdminUser("admin-api@example.com");
        String accessToken = jwtTokenProvider.createAccessToken(admin);

        String requestBody = """
                {
                  "name": "Admin Managed Skill",
                  "normalizedName": "admin managed skill",
                  "category": "FRAMEWORK"
                }
                """;

        mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Admin Managed Skill"));
    }

    @Test
    @DisplayName("ADMIN API는 토큰이 없으면 401, USER 토큰이면 403으로 실패한다")
    void adminApiAuthBoundary() throws Exception {
        User user = saveLocalUser("admin-boundary-user@example.com");
        String userToken = jwtTokenProvider.createAccessToken(user);

        String requestBody = """
                {
                  "name": "Boundary Skill",
                  "normalizedName": "boundary skill",
                  "category": "FRAMEWORK"
                }
                """;

        mockMvc.perform(post("/skills")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_UNAUTHORIZED"));

        mockMvc.perform(post("/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_FORBIDDEN"));
    }

    private User saveLocalUser(String email) {
        User user = User.signup(
                email,
                "encoded-password",
                "테스트"
        );

        return userRepository.save(user);
    }

    private User saveAdminUser(String email) {
        User user = User.signup(
                email,
                "encoded-password",
                "관리자"
        );
        ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);

        return userRepository.save(user);
    }

    private String createExpiredToken(User user) {
        Date now = new Date();
        Date issuedAt = new Date(now.getTime() - 7_200_000L);
        Date expiration = new Date(now.getTime() - 3_600_000L);

        SecretKey secretKey = Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
