package jobflow.domain.auth.oauth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AesGcmOAuth2ProviderTokenEncryptorTest {

    @Test
    @DisplayName("provider access token을 암호화하고 복호화한다")
    void encryptAndDecrypt() {
        AesGcmOAuth2ProviderTokenEncryptor encryptor = new AesGcmOAuth2ProviderTokenEncryptor(
                new OAuth2ProviderTokenCryptoProperties("test-encryption-key")
        );

        String encrypted = encryptor.encrypt("github-provider-access-token");

        assertThat(encrypted).isNotEqualTo("github-provider-access-token");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("github-provider-access-token");
    }

    @Test
    @DisplayName("같은 provider access token도 매번 다른 ciphertext로 암호화한다")
    void encryptWithRandomIv() {
        AesGcmOAuth2ProviderTokenEncryptor encryptor = new AesGcmOAuth2ProviderTokenEncryptor(
                new OAuth2ProviderTokenCryptoProperties("test-encryption-key")
        );

        String first = encryptor.encrypt("github-provider-access-token");
        String second = encryptor.encrypt("github-provider-access-token");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptor.decrypt(first)).isEqualTo("github-provider-access-token");
        assertThat(encryptor.decrypt(second)).isEqualTo("github-provider-access-token");
    }

    @Test
    @DisplayName("암호화 key가 없으면 provider token을 처리하지 않는다")
    void encryptWithoutKey() {
        AesGcmOAuth2ProviderTokenEncryptor encryptor = new AesGcmOAuth2ProviderTokenEncryptor(
                new OAuth2ProviderTokenCryptoProperties("")
        );

        assertThatThrownBy(() -> encryptor.encrypt("github-provider-access-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_KEY_MISSING);
    }
}
