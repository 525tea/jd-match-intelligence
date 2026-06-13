package jobflow.domain.auth.oauth;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class GitHubOAuth2EmailClient {

    private static final String EMAILS_URL = "https://api.github.com/user/emails";

    private final RestClient restClient;

    public GitHubOAuth2EmailClient() {
        this.restClient = RestClient.builder().build();
    }

    public Optional<String> fetchPrimaryVerifiedEmail(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return Optional.empty();
        }

        GitHubEmailResponse[] emails = restClient.get()
                .uri(EMAILS_URL)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(GitHubEmailResponse[].class);

        if (emails == null) {
            return Optional.empty();
        }

        return Arrays.stream(emails)
                .filter(email -> Boolean.TRUE.equals(email.primary()))
                .filter(email -> Boolean.TRUE.equals(email.verified()))
                .map(GitHubEmailResponse::email)
                .filter(StringUtils::hasText)
                .findFirst();
    }
}
