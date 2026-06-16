package jobflow.domain.notification;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
        prefix = "app.notification",
        name = "email-provider",
        havingValue = "mock"
)
public class MockEmailSender implements EmailSender {

    static final String PROVIDER = "MOCK_EMAIL";

    private final MockEmailSenderProperties properties;
    private final AtomicLong sequence = new AtomicLong();

    public MockEmailSender(MockEmailSenderProperties properties) {
        this.properties = properties;
    }

    @Override
    public EmailSendResult send(EmailSendRequest request) {
        if (!isValid(request)) {
            return EmailSendResult.failed(PROVIDER, "Email request is invalid");
        }
        if (properties.fail()) {
            return EmailSendResult.failed(PROVIDER, properties.failureReason());
        }

        long currentSequence = sequence.incrementAndGet();
        String messageId = properties.providerMessageIdPrefix() + "-" + currentSequence;
        return EmailSendResult.sent(PROVIDER, messageId);
    }

    private boolean isValid(EmailSendRequest request) {
        return request != null
                && StringUtils.hasText(request.to())
                && StringUtils.hasText(request.subject())
                && StringUtils.hasText(request.text());
    }
}
