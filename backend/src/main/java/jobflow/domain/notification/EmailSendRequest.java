package jobflow.domain.notification;

public record EmailSendRequest(
        String to,
        String subject,
        String text,
        String html
) {
}
