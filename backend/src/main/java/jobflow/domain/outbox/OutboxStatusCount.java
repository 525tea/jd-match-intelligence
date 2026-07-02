package jobflow.domain.outbox;

public interface OutboxStatusCount {

    OutboxStatus getStatus();

    long getCount();
}
