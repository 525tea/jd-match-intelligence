package jobflow.domain.auth.oauth.token;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import jobflow.global.error.ErrorCode;
import jobflow.global.error.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@EnableConfigurationProperties(OAuth2ProviderTokenCryptoProperties.class)
public class AesGcmOAuth2ProviderTokenEncryptor implements OAuth2ProviderTokenEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;

    private final OAuth2ProviderTokenCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmOAuth2ProviderTokenEncryptor(OAuth2ProviderTokenCryptoProperties properties) {
        this.properties = properties;
    }

    @Override
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
        }

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_CRYPTO_FAILED);
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        if (!StringUtils.hasText(encryptedText)) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedText);
            if (payload.length <= IV_LENGTH_BYTES) {
                throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_INVALID);
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_CRYPTO_FAILED);
        }
    }

    private SecretKeySpec keySpec() throws GeneralSecurityException {
        if (!StringUtils.hasText(properties.encryptionKey())) {
            throw new BusinessException(ErrorCode.AUTH_OAUTH2_PROVIDER_TOKEN_KEY_MISSING);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(properties.encryptionKey().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, ALGORITHM);
    }
}
