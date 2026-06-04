package jobflow.collector.job.ingest;

public interface CrawlerHttpClient {

    CrawlerHttpResponse get(String url);
}
