package jobflow.domain.job.ingest;

public interface CrawlerHttpClient {

    CrawlerHttpResponse get(String url);
}
