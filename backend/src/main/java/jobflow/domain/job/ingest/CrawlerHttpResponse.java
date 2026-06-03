package jobflow.domain.job.ingest;

public record CrawlerHttpResponse(
        int statusCode,
        String body
) {

    public boolean isSuccessful() {
        return statusCode >= 200 && statusCode < 300;
    }
}
