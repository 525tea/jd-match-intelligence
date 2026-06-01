package jobflow.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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

    private User saveLocalUser(String email) {
        User user = User.signup(
                email,
                "encoded-password",
                "테스트"
        );

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
