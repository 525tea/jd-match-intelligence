package jobflow.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("LOCAL 회원가입 사용자를 생성한다")
    void signup() {
        User user = User.signup(
                "test@example.com",
                "encoded-password",
                "테스트"
        );

        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getName()).isEqualTo("테스트");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getProviderId()).isNull();
    }

    @Test
    @DisplayName("OAuth2 사용자를 생성한다")
    void oauth2() {
        User user = User.oauth2(
                "oauth@example.com",
                "OAuth User",
                AuthProvider.GITHUB,
                "github-123"
        );

        assertThat(user.getEmail()).isEqualTo("oauth@example.com");
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getName()).isEqualTo("OAuth User");
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.getAuthProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(user.getProviderId()).isEqualTo("github-123");
    }
}
