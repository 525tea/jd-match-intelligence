package jobflow.domain.notification;

public record EmailSendResult(
        boolean success,
        String provider,
        String providerMessageId,
        String failureReason
) {

    public static EmailSendResult sent(String provider, String providerMessageId) {
        return new EmailSendResult(true, provider, providerMessageId, null);
    }

    public static EmailSendResult failed(String provider, String failureReason) {
        return new EmailSendResult(false, provider, null, failureReason);
    }
}
