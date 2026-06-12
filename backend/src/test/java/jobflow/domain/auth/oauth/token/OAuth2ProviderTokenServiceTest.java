package jobflow.domain.auth.oauth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import jobflow.domain.user.AuthProvider;
import jobflow.domain.user.User;
import jobflow.domain.user.UserRepository;
import jobflow.global.config.JpaAuditingConfig;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
        JpaAuditingConfig.class,
        OAuth2ProviderTokenService.class,
        AesGcmOAuth2ProviderTokenEncryptor.class
})
class OAuth2ProviderTokenServiceTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOAuthTokenRepository userOAuthTokenRepository;

    @Autowired
    private OAuth2ProviderTokenService tokenService;

    @Test
    @DisplayName("GitHub provider access token을 암호화 저장하고 복호화 조회한다")
    void saveAndFindAccessToken() {
        User user = userRepository.save(User.oauth2(
                "github@example.com",
                "github-user",
                AuthProvider.GITHUB,
                "github-123"
        ));

        tokenService.saveOrReplace(new OAuth2ProviderTokenCommand(
                user.getId(),
                AuthProvider.GITHUB,
                "github-provider-access-token",
                "Bearer",
                List.of("repo", "read:user"),
                LocalDateTime.of(2026, 6, 12, 10, 0),
                LocalDateTime.of(2026, 6, 12, 11, 0)
        ));

        UserOAuthToken savedToken = userOAuthTokenRepository
                .findByUserIdAndAuthProvider(user.getId(), AuthProvider.GITHUB)
                .orElseThrow();

        assertThat(savedToken.getEncryptedAccessToken()).isNotEqualTo("github-provider-access-token");
        assertThat(savedToken.getScopes()).isEqualTo("read:user repo");
        assertThat(tokenService.findAccessToken(user.getId(), AuthProvider.GITHUB))
                .contains("github-provider-access-token");
    }

    @Test
    @DisplayName("재연동 시 기존 GitHub provider access token을 교체한다")
    void replaceAccessToken() {
        User user = userRepository.save(User.oauth2(
                "github@example.com",
                "github-user",
                AuthProvider.GITHUB,
                "github-123"
        ));

        tokenService.saveOrReplace(new OAuth2ProviderTokenCommand(
                user.getId(),
                AuthProvider.GITHUB,
                "old-github-provider-token",
                "Bearer",
                List.of("read:user"),
                LocalDateTime.of(2026, 6, 12, 10, 0),
                LocalDateTime.of(2026, 6, 12, 11, 0)
        ));

        tokenService.saveOrReplace(new OAuth2ProviderTokenCommand(
                user.getId(),
                AuthProvider.GITHUB,
                "new-github-provider-token",
                "Bearer",
                List.of("repo", "read:user"),
                LocalDateTime.of(2026, 6, 12, 12, 0),
                LocalDateTime.of(2026, 6, 12, 13, 0)
        ));

        assertThat(userOAuthTokenRepository.findAll()).hasSize(1);
        assertThat(tokenService.findAccessToken(user.getId(), AuthProvider.GITHUB))
                .contains("new-github-provider-token");

        UserOAuthToken savedToken = userOAuthTokenRepository
                .findByUserIdAndAuthProvider(user.getId(), AuthProvider.GITHUB)
                .orElseThrow();

        assertThat(savedToken.getScopes()).isEqualTo("read:user repo");
        assertThat(savedToken.getIssuedAt()).isEqualTo(LocalDateTime.of(2026, 6, 12, 12, 0));
    }

    @Test
    @DisplayName("연동 해제 시 GitHub provider access token을 삭제한다")
    void deleteAccessToken() {
        User user = userRepository.save(User.oauth2(
                "github@example.com",
                "github-user",
                AuthProvider.GITHUB,
                "github-123"
        ));

        tokenService.saveOrReplace(new OAuth2ProviderTokenCommand(
                user.getId(),
                AuthProvider.GITHUB,
                "github-provider-access-token",
                "Bearer",
                List.of("read:user"),
                LocalDateTime.of(2026, 6, 12, 10, 0),
                null
        ));

        tokenService.delete(user.getId(), AuthProvider.GITHUB);

        assertThat(tokenService.findAccessToken(user.getId(), AuthProvider.GITHUB)).isEmpty();
        assertThat(userOAuthTokenRepository.findByUserIdAndAuthProvider(user.getId(), AuthProvider.GITHUB)).isEmpty();
    }

    @Test
    @DisplayName("필수 provider access token이 없으면 명확한 domain error가 발생한다")
    void getRequiredAccessTokenWithoutToken() {
        User user = userRepository.save(User.oauth2(
                "missing-token@example.com",
                "github-user",
                AuthProvider.GITHUB,
                "github-missing-token"
        ));

        assertThatThrownBy(() -> tokenService.getRequiredAccessToken(user.getId(), AuthProvider.GITHUB))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_NOT_FOUND);
    }

    @Test
    @DisplayName("LOCAL provider token 저장은 허용하지 않는다")
    void saveLocalProviderToken() {
        User user = userRepository.save(User.signup(
                "local@example.com",
                "password-hash",
                "local-user"
        ));

        OAuth2ProviderTokenCommand command = new OAuth2ProviderTokenCommand(
                user.getId(),
                AuthProvider.LOCAL,
                "local-token",
                "Bearer",
                List.of(),
                LocalDateTime.of(2026, 6, 12, 10, 0),
                null
        );

        assertThatThrownBy(() -> tokenService.saveOrReplace(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_OAUTH2_PROVIDER_NOT_SUPPORTED);
    }

}
