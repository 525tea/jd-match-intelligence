package jobflow.collector.job.ingest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RobotsPolicyService {

    private final CrawlerProperties crawlerProperties;
    private final CrawlerHttpClient httpClient;
    private final RobotsTxtParser robotsTxtParser;
    private final Map<JobIngestionSource, RobotsTxt> robotsTxtCache = new ConcurrentHashMap<>();

    public RobotsPolicyService(
            CrawlerProperties crawlerProperties,
            CrawlerHttpClient httpClient,
            RobotsTxtParser robotsTxtParser
    ) {
        this.crawlerProperties = crawlerProperties;
        this.httpClient = httpClient;
        this.robotsTxtParser = robotsTxtParser;
    }

    public boolean isAllowed(JobIngestionSource source, String url) {
        CrawlerSourcePolicy policy = crawlerProperties.policy(source);
        URI uri = URI.create(url.trim()).normalize();

        if (!isHttp(uri) || !isSameHost(policy.baseUrl(), uri)) {
            return false;
        }

        String path = pathWithQuery(uri);
        RobotsTxt robotsTxt = loadRobotsTxt(source, policy);

        return robotsTxt.isAllowed(path);
    }

    public void assertAllowed(JobIngestionSource source, String url) {
        if (!isAllowed(source, url)) {
            throw new RobotsPolicyException(
                    "URL is disallowed by robots.txt. source=" + source + ", url=" + url
            );
        }
    }

    public void refresh(JobIngestionSource source) {
        CrawlerSourcePolicy policy = crawlerProperties.policy(source);

        robotsTxtCache.put(source, fetchRobotsTxt(source, policy));
    }

    public void clearCache() {
        robotsTxtCache.clear();
    }

    private RobotsTxt loadRobotsTxt(JobIngestionSource source, CrawlerSourcePolicy policy) {
        return robotsTxtCache.computeIfAbsent(source, ignored -> fetchRobotsTxt(source, policy));
    }

    private RobotsTxt fetchRobotsTxt(JobIngestionSource source, CrawlerSourcePolicy policy) {
        CrawlerHttpResponse response = httpClient.get(policy.robotsUrl());

        if (!response.isSuccessful()) {
            if (source == JobIngestionSource.WANTED && response.statusCode() == 403) {
                return fallbackRobotsTxt(policy);
            }

            throw new RobotsPolicyException(
                    "Failed to fetch robots.txt. source="
                            + source
                            + ", robotsUrl="
                            + policy.robotsUrl()
                            + ", statusCode="
                            + response.statusCode()
            );
        }

        return robotsTxtParser.parse(response.body(), crawlerProperties.userAgent());
    }

    private RobotsTxt fallbackRobotsTxt(CrawlerSourcePolicy policy) {
        List<RobotsPathRule> rules = new ArrayList<>();

        rules.add(new RobotsPathRule(false, "/"));
        policy.allowedPathPrefixes()
                .forEach(path -> rules.add(new RobotsPathRule(true, path)));
        policy.disallowedPathPrefixes()
                .forEach(path -> rules.add(new RobotsPathRule(false, path)));

        return new RobotsTxt(rules, List.of());
    }

    private String pathWithQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank()
                ? "/"
                : uri.getRawPath();

        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return path;
        }

        return path + "?" + uri.getRawQuery();
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
}
