package jobflow.collector.job.ingest;

import java.util.List;

public record ParsedSitemap(
        SitemapType type,
        List<SitemapEntry> entries
) {

    public boolean isUrlSet() {
        return type == SitemapType.URL_SET;
    }

    public boolean isSitemapIndex() {
        return type == SitemapType.SITEMAP_INDEX;
    }
}
