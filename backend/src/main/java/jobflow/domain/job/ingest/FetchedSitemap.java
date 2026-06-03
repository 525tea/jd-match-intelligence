package jobflow.domain.job.ingest;

public record FetchedSitemap(
        JobIngestionSource source,
        String sitemapUrl,
        ParsedSitemap sitemap
) {
}
