package jobflow.domain.auth.oauth.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.oauth2.provider-token")
public record OAuth2ProviderTokenCryptoProperties(
        String encryptionKey
) {
}
