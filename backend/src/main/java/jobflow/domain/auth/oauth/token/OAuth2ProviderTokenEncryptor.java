package jobflow.domain.auth.oauth.token;

public interface OAuth2ProviderTokenEncryptor {

    String encrypt(String plainText);

    String decrypt(String encryptedText);
}
