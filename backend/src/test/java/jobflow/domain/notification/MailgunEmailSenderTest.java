package jobflow.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailgunEmailSenderTest {

    private MockRestServiceServer server;
    private MailgunEmailSender sender;

    @BeforeEach
    void setUp() {
        MailgunProperties properties = new MailgunProperties(
                "https://api.mailgun.test",
                "mail.example.test",
                "test-mailgun-api-key",
                "JobFlow <no-reply@example.test>"
        );
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new MailgunEmailSender(properties, builder.build());
    }

    @Test
    @DisplayName("Mailgun messages API로 email을 발송하고 provider message id를 반환한다")
    void send() {
        server.expect(once(), requestTo("https://api.mailgun.test/v3/mail.example.test/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, basicAuth("api", "test-mailgun-api-key")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("to=user%40example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("subject=%5BJobFlow%5D+Deadline+reminder")))
                .andRespond(withSuccess("""
                        {
                          "id": "<message-id@example.test>",
                          "message": "Queued. Thank you."
                        }
                        """, MediaType.APPLICATION_JSON));

        EmailSendResult result = sender.send(new EmailSendRequest(
                "user@example.com",
                "[JobFlow] Deadline reminder",
                "A saved job is closing soon.",
                "<p>A saved job is closing soon.</p>"
        ));

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(MailgunEmailSender.PROVIDER);
        assertThat(result.providerMessageId()).isEqualTo("<message-id@example.test>");
        assertThat(result.failureReason()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("Mailgun 오류 응답은 실패 결과로 변환한다")
    void sendFailed() {
        server.expect(once(), requestTo("https://api.mailgun.test/v3/mail.example.test/messages"))
                .andRespond(withServerError());

        EmailSendResult result = sender.send(new EmailSendRequest(
                "user@example.com",
                "[JobFlow] Deadline reminder",
                "A saved job is closing soon.",
                null
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(MailgunEmailSender.PROVIDER);
        assertThat(result.providerMessageId()).isNull();
        assertThat(result.failureReason()).isEqualTo("Mailgun request failed. status=500");
        server.verify();
    }

    @Test
    @DisplayName("Mailgun 필수 설정이 없으면 외부 호출 없이 실패 결과를 반환한다")
    void sendWithoutConfig() {
        MailgunEmailSender unconfiguredSender = new MailgunEmailSender(
                new MailgunProperties("https://api.mailgun.test", null, null, "no-reply@example.test"),
                RestClient.builder().baseUrl("https://api.mailgun.test").build()
        );

        EmailSendResult result = unconfiguredSender.send(new EmailSendRequest(
                "user@example.com",
                "[JobFlow] Deadline reminder",
                "A saved job is closing soon.",
                null
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("Mailgun properties are not configured");
    }

    @Test
    @DisplayName("필수 email request 값이 없으면 외부 호출 없이 실패 결과를 반환한다")
    void sendInvalidRequest() {
        EmailSendResult result = sender.send(new EmailSendRequest(
                "",
                "[JobFlow] Deadline reminder",
                "A saved job is closing soon.",
                null
        ));

        assertThat(result.success()).isFalse();
        assertThat(result.failureReason()).isEqualTo("Email request is invalid");
    }

    private String basicAuth(String username, String password) {
        String token = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }
}
