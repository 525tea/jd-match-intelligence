package jobflow.domain.auth.oauth.code;

public interface OAuth2AuthorizationCodeStore {

    OAuth2AuthorizationCode save(Long userId);

    OAuth2AuthorizationCode consume(String code);
}
