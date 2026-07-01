package jobflow.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import jobflow.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenProviderTest {

    private static final String TEST_JWT_SECRET = "jobflow-test-secret-key-must-be-at-least-32-bytes-long";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
            TEST_JWT_SECRET,
            3_600_000L
    ));

    @Test
    @DisplayName("access token claim으로 DB 조회 없이 UserPrincipal을 복원한다")
    void getPrincipalFromAccessTokenClaims() {
        User user = User.signup(
                "user@example.com",
                "encoded-password",
                "Example User"
        );
        ReflectionTestUtils.setField(user, "id", 7L);

        String accessToken = jwtTokenProvider.createAccessToken(user);

        assertThat(jwtTokenProvider.getPrincipal(accessToken))
                .hasValueSatisfying(principal -> {
                    assertThat(principal.id()).isEqualTo(7L);
                    assertThat(principal.email()).isEqualTo("user@example.com");
                    assertThat(principal.name()).isEqualTo("Example User");
                    assertThat(principal.role()).isEqualTo("USER");
                });
    }

    @Test
    @DisplayName("name claim이 없는 기존 access token은 email을 name fallback으로 사용한다")
    void getPrincipalFallsBackToEmailWhenLegacyTokenHasNoNameClaim() {
        String accessToken = Jwts.builder()
                .subject("9")
                .claim("email", "legacy-user@example.com")
                .claim("role", "USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtTokenProvider.getPrincipal(accessToken))
                .hasValueSatisfying(principal -> {
                    assertThat(principal.id()).isEqualTo(9L);
                    assertThat(principal.email()).isEqualTo("legacy-user@example.com");
                    assertThat(principal.name()).isEqualTo("legacy-user@example.com");
                    assertThat(principal.role()).isEqualTo("USER");
                });
    }

    @Test
    @DisplayName("필수 claim이 없으면 UserPrincipal을 만들지 않는다")
    void getPrincipalReturnsEmptyWhenRequiredClaimsAreMissing() {
        String accessToken = Jwts.builder()
                .subject("11")
                .claim("email", "missing-role@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(jwtTokenProvider.getPrincipal(accessToken)).isEmpty();
    }
}
