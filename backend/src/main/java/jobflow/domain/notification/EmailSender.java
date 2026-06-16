package jobflow.domain.notification;

public interface EmailSender {

    EmailSendResult send(EmailSendRequest request);
}
