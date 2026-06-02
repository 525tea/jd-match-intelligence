package jobflow.domain.outbox;

public interface OutboxEventHandler {

    boolean supports(OutboxEvent event);

    void handle(OutboxEvent event);
}
