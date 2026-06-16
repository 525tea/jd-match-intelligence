package jobflow.domain.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.notification",
        name = "email-provider",
        havingValue = "mailgun",
        matchIfMissing = true
)
public class MailgunEmailSender implements EmailSender {

    static final String PROVIDER = "MAILGUN";

    private final MailgunProperties properties;
    private final RestClient restClient;

    @Autowired
    public MailgunEmailSender(MailgunProperties properties) {
        this(properties, RestClient.builder()
                .baseUrl(properties.baseUrl())
                .build());
    }

    MailgunEmailSender(MailgunProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public EmailSendResult send(EmailSendRequest request) {
        if (!properties.isConfigured()) {
            return EmailSendResult.failed(PROVIDER, "Mailgun properties are not configured");
        }
        if (!isValid(request)) {
            return EmailSendResult.failed(PROVIDER, "Email request is invalid");
        }

        try {
            MailgunSendResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .pathSegment("v3", properties.domain(), "messages")
                            .build())
                    .headers(headers -> headers.setBasicAuth("api", properties.apiKey()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form(request))
                    .retrieve()
                    .body(MailgunSendResponse.class);

            return EmailSendResult.sent(PROVIDER, response == null ? null : response.id());
        } catch (RestClientResponseException exception) {
            log.warn(
                    "Mailgun send request failed. status={}, response={}",
                    exception.getStatusCode(),
                    exception.getResponseBodyAsString()
            );
            return EmailSendResult.failed(
                    PROVIDER,
                    "Mailgun request failed. status=" + exception.getStatusCode().value()
            );
        } catch (RestClientException exception) {
            log.warn("Mailgun send request failed.", exception);
            return EmailSendResult.failed(PROVIDER, "Mailgun request failed");
        }
    }

    private boolean isValid(EmailSendRequest request) {
        return request != null
                && StringUtils.hasText(request.to())
                && StringUtils.hasText(request.subject())
                && StringUtils.hasText(request.text());
    }

    private MultiValueMap<String, String> form(EmailSendRequest request) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("from", properties.from());
        form.add("to", request.to());
        form.add("subject", request.subject());
        form.add("text", request.text());
        if (StringUtils.hasText(request.html())) {
            form.add("html", request.html());
        }
        return form;
    }

    record MailgunSendResponse(
            @JsonProperty("id") String id,
            @JsonProperty("message") String message
    ) {
    }
}
