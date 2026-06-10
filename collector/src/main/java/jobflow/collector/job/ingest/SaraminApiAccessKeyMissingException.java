package jobflow.collector.job.ingest;

public class SaraminApiAccessKeyMissingException extends RuntimeException {

    public SaraminApiAccessKeyMissingException() {
        super("Saramin API access key is missing. Set SARAMIN_ACCESS_KEY before running source=SARAMIN.");
    }
}
