package jobflow.domain.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NoopOutboxEventHandler implements OutboxEventHandler {

    @Override
    public boolean supports(OutboxEvent event) {
        return true;
    }

    @Override
    public void handle(OutboxEvent event) {
        log.info(
                "Outbox event handled by noop handler. eventId={}, topic={}, eventType={}, aggregateType={}, aggregateId={}",
                event.getId(),
                event.getTopic(),
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId()
        );
    }
}
