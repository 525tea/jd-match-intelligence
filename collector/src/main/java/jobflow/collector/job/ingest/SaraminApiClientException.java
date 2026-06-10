package jobflow.collector.job.ingest;

public class SaraminApiClientException extends RuntimeException {

    public SaraminApiClientException(String message) {
        super(message);
    }

    public SaraminApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
