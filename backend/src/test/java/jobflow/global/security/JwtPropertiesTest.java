package jobflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    @Test
    @DisplayName("JWT secret이 없으면 설정 오류로 실패한다")
    void rejectBlankSecret() {
        assertThatThrownBy(() -> new JwtProperties("", 3_600_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must be configured.");
    }

    @Test
    @DisplayName("JWT secret이 32바이트 미만이면 설정 오류로 실패한다")
    void rejectShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("short-secret", 3_600_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT secret must be at least 32 bytes.");
    }

    @Test
    @DisplayName("JWT secret이 32바이트 이상이면 설정을 허용한다")
    void allowStrongSecret() {
        assertThatCode(() -> new JwtProperties(
                "jwt-test-secret-key-must-be-at-least-32-bytes",
                3_600_000L
        )).doesNotThrowAnyException();
    }
}
