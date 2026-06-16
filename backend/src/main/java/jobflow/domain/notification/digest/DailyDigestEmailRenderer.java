package jobflow.domain.notification.digest;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import jobflow.domain.notification.EmailSendRequest;
import org.springframework.stereotype.Component;

@Component
public class DailyDigestEmailRenderer {

    private static final DateTimeFormatter DEADLINE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public EmailSendRequest render(
            String userEmail,
            String userName,
            DailyDigestContent content
    ) {
        String displayName = displayName(userName);
        String subject = "[JobFlow] 오늘의 맞춤 공고 Digest";
        String text = renderText(displayName, content);
        String html = renderHtml(displayName, content);

        return new EmailSendRequest(userEmail, subject, text, html);
    }

    private String renderText(String displayName, DailyDigestContent content) {
        if (content.isEmpty()) {
            return """
                    %s님, 오늘은 새로 추천할 공고가 없습니다.

                    저장한 프로젝트와 관심 공고가 늘어나면 더 정확한 Digest를 보내드릴게요.
                    """.formatted(displayName);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(displayName)
                .append("님, 오늘 확인하면 좋은 공고를 모았어요.\n\n");

        appendTextSection(builder, "추천 공고", content.recommendedJobs());
        appendTextSection(builder, "JD 매칭 공고", content.jdMatchJobs());
        appendTextSection(builder, "신규 공고", content.newJobs());
        appendTextSection(builder, "마감 임박 저장 공고", content.deadlineReminderJobs());

        return builder.toString().stripTrailing();
    }

    private void appendTextSection(
            StringBuilder builder,
            String title,
            List<DailyDigestJobItem> items
    ) {
        if (items.isEmpty()) {
            return;
        }

        builder.append("## ").append(title).append("\n");
        for (int index = 0; index < items.size(); index++) {
            DailyDigestJobItem item = items.get(index);
            builder.append(index + 1)
                    .append(". ")
                    .append(item.title())
                    .append(" / ")
                    .append(item.companyName())
                    .append("\n")
                    .append("   역할: ")
                    .append(item.role())
                    .append(" · 경력: ")
                    .append(item.careerLevel())
                    .append("\n");

            if (item.score() != null) {
                builder.append("   점수: ")
                        .append(formatScore(item.score()))
                        .append("\n");
            }

            if (item.deadlineAt() != null) {
                builder.append("   마감: ")
                        .append(item.deadlineAt().format(DEADLINE_FORMATTER))
                        .append("\n");
            }

            if (item.reason() != null && !item.reason().isBlank()) {
                builder.append("   이유: ")
                        .append(item.reason())
                        .append("\n");
            }

            builder.append("   링크: ")
                    .append(fallbackUrl(item.originalUrl()))
                    .append("\n");
        }
        builder.append("\n");
    }

    private String renderHtml(String displayName, DailyDigestContent content) {
        if (content.isEmpty()) {
            return """
                    <p>%s님, 오늘은 새로 추천할 공고가 없습니다.</p>
                    <p>저장한 프로젝트와 관심 공고가 늘어나면 더 정확한 Digest를 보내드릴게요.</p>
                    """.formatted(escape(displayName));
        }

        StringBuilder builder = new StringBuilder();
        builder.append("<p>")
                .append(escape(displayName))
                .append("님, 오늘 확인하면 좋은 공고를 모았어요.</p>");

        appendHtmlSection(builder, "추천 공고", content.recommendedJobs());
        appendHtmlSection(builder, "JD 매칭 공고", content.jdMatchJobs());
        appendHtmlSection(builder, "신규 공고", content.newJobs());
        appendHtmlSection(builder, "마감 임박 저장 공고", content.deadlineReminderJobs());

        return builder.toString();
    }

    private void appendHtmlSection(
            StringBuilder builder,
            String title,
            List<DailyDigestJobItem> items
    ) {
        if (items.isEmpty()) {
            return;
        }

        builder.append("<h2>")
                .append(escape(title))
                .append("</h2>")
                .append("<ol>");

        for (DailyDigestJobItem item : items) {
            builder.append("<li>")
                    .append("<strong>")
                    .append(escape(item.title()))
                    .append("</strong>")
                    .append(" / ")
                    .append(escape(item.companyName()))
                    .append("<br>")
                    .append("역할: ")
                    .append(escape(item.role().name()))
                    .append(" · 경력: ")
                    .append(escape(item.careerLevel().name()));

            if (item.score() != null) {
                builder.append("<br>")
                        .append("점수: ")
                        .append(escape(formatScore(item.score())));
            }

            if (item.deadlineAt() != null) {
                builder.append("<br>")
                        .append("마감: ")
                        .append(escape(item.deadlineAt().format(DEADLINE_FORMATTER)));
            }

            if (item.reason() != null && !item.reason().isBlank()) {
                builder.append("<br>")
                        .append("이유: ")
                        .append(escape(item.reason()));
            }

            builder.append("<br>")
                    .append("링크: ")
                    .append(renderHtmlLink(item.originalUrl()))
                    .append("</li>");
        }

        builder.append("</ol>");
    }

    private String renderHtmlLink(String originalUrl) {
        String url = fallbackUrl(originalUrl);
        if ("링크 없음".equals(url)) {
            return url;
        }

        return "<a href=\"%s\">%s</a>".formatted(
                escape(url),
                escape(url)
        );
    }

    private String displayName(String userName) {
        if (userName == null || userName.isBlank()) {
            return "사용자";
        }
        return userName;
    }

    private String fallbackUrl(String originalUrl) {
        return originalUrl == null || originalUrl.isBlank() ? "링크 없음" : originalUrl;
    }

    private String formatScore(BigDecimal score) {
        return score.stripTrailingZeros().toPlainString() + "점";
    }

    private String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
