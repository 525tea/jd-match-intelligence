package jobflow.domain.notification;

import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
public class DeadlineReminderEmailRenderer {

    private static final DateTimeFormatter DEADLINE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public EmailSendRequest render(DeadlineReminderTarget target) {
        String subject = "[JobFlow] 저장한 공고 마감이 다가오고 있어요";
        String deadline = target.deadlineAt().format(DEADLINE_FORMATTER);
        String text = """
                저장한 공고의 지원 마감이 다가오고 있어요.

                공고: %s
                회사: %s
                마감: %s
                링크: %s
                """.formatted(
                target.jobTitle(),
                target.companyName(),
                deadline,
                fallbackUrl(target.originalUrl())
        );
        String html = """
                <p>저장한 공고의 지원 마감이 다가오고 있어요.</p>
                <ul>
                  <li>공고: %s</li>
                  <li>회사: %s</li>
                  <li>마감: %s</li>
                  <li>링크: <a href="%s">%s</a></li>
                </ul>
                """.formatted(
                escape(target.jobTitle()),
                escape(target.companyName()),
                escape(deadline),
                escape(fallbackUrl(target.originalUrl())),
                escape(fallbackUrl(target.originalUrl()))
        );

        return new EmailSendRequest(target.userEmail(), subject, text, html);
    }

    private String fallbackUrl(String originalUrl) {
        return originalUrl == null || originalUrl.isBlank() ? "링크 없음" : originalUrl;
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
