package jobflow.domain.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubEmailResponse(
        String email,
        Boolean primary,
        Boolean verified,
        String visibility,
        @JsonProperty("type") String type
) {
}
