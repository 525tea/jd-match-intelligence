package jobflow.collector.job.ingest;

import java.net.URI;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CrawlerUrlNormalizer {

    private final CrawlerProperties crawlerProperties;
    private final CrawlerExternalIdExtractor externalIdExtractor;

    public CrawlerUrlNormalizer(CrawlerProperties crawlerProperties) {
        this.crawlerProperties = crawlerProperties;
        this.externalIdExtractor = new CrawlerExternalIdExtractor();
    }

    public Optional<CrawlerUrlCandidate> normalize(JobIngestionSource source, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.empty();
        }

        CrawlerSourcePolicy policy = crawlerProperties.policy(source);
        URI uri = resolve(policy.baseUrl(), rawUrl.trim());

        if (!isHttp(uri) || !isSameHost(policy.baseUrl(), uri)) {
            return Optional.empty();
        }

        String path = uri.getPath();

        if (!policy.isAllowedPath(path)) {
            return Optional.empty();
        }

        return externalIdExtractor.extract(source, uri)
                .map(externalId -> new CrawlerUrlCandidate(
                        source,
                        removeFragment(uri).toString(),
                        toDetailUrl(source, uri),
                        externalId
                ));
    }

    private URI resolve(String baseUrl, String rawUrl) {
        URI uri = URI.create(rawUrl);

        if (uri.isAbsolute()) {
            return uri.normalize();
        }

        return URI.create(baseUrl).resolve(uri).normalize();
    }

    private boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
    }

    private boolean isSameHost(String baseUrl, URI uri) {
        String baseHost = URI.create(baseUrl).getHost();
        String uriHost = uri.getHost();

        return baseHost != null && baseHost.equalsIgnoreCase(uriHost);
    }

    private URI removeFragment(URI uri) {
        return URI.create(uri.toString().split("#", 2)[0]);
    }

    private String toDetailUrl(JobIngestionSource source, URI uri) {
        URI withoutFragment = removeFragment(uri);
        String baseDetailUrl = withoutFragment.getScheme()
                + "://"
                + withoutFragment.getHost()
                + withoutFragment.getPath();

        if (source == JobIngestionSource.SARAMIN) {
            return externalIdExtractor.extract(source, withoutFragment)
                    .map(externalId -> baseDetailUrl + "?rec_idx=" + externalId)
                    .orElse(baseDetailUrl);
        }

        return baseDetailUrl;
    }
}
