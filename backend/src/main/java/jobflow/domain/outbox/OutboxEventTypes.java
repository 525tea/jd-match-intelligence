package jobflow.domain.outbox;

public final class OutboxEventTypes {

    public static final String JOB_CREATED = "JOB_CREATED";
    public static final String JOB_UPDATED = "JOB_UPDATED";
    public static final String JOB_CLOSED = "JOB_CLOSED";
    public static final String JOB_EXPIRED = "JOB_EXPIRED";

    public static final String APPLICATION_CREATED = "APPLICATION_CREATED";
    public static final String APPLICATION_STATUS_CHANGED = "APPLICATION_STATUS_CHANGED";

    private OutboxEventTypes() {
    }
}
