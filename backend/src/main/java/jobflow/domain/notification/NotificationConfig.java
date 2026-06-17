package jobflow.domain.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        DeadlineReminderProperties.class,
        DailyDigestProperties.class,
        MailgunProperties.class,
        MockEmailSenderProperties.class
})
public class NotificationConfig {
}
