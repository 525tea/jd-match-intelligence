package jobflow.collector.job.ingest;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

public record RobotsTxt(
        List<RobotsPathRule> rules,
        List<String> sitemapUrls
) {

    public boolean isAllowed(String urlOrPath) {
        String path = normalizePath(urlOrPath);

        return rules.stream()
                .filter(rule -> path.startsWith(rule.path()))
                .max(Comparator
                        .comparingInt(RobotsPathRule::length)
                        .thenComparing(RobotsPathRule::allow))
                .map(RobotsPathRule::allow)
                .orElse(true);
    }

    private String normalizePath(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            return "/";
        }

        String trimmed = urlOrPath.trim();

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            URI uri = URI.create(trimmed);
            String path = uri.getRawPath() == null || uri.getRawPath().isBlank()
                    ? "/"
                    : uri.getRawPath();

            if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
                return path;
            }

            return path + "?" + uri.getRawQuery();
        }

        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}
