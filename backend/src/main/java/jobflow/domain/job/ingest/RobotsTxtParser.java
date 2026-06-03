package jobflow.domain.job.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RobotsTxtParser {

    public RobotsTxt parse(String robotsTxt, String userAgent) {
        if (robotsTxt == null || robotsTxt.isBlank()) {
            return new RobotsTxt(List.of(), List.of());
        }

        List<RobotsGroup> groups = new ArrayList<>();
        List<String> sitemapUrls = new ArrayList<>();
        RobotsGroup currentGroup = null;

        for (String rawLine : robotsTxt.lines().toList()) {
            String line = stripComment(rawLine).trim();

            if (line.isBlank() || !line.contains(":")) {
                continue;
            }

            String[] parts = line.split(":", 2);
            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();

            if ("sitemap".equals(key)) {
                if (!value.isBlank()) {
                    sitemapUrls.add(value);
                }
                continue;
            }

            if ("user-agent".equals(key)) {
                if (currentGroup == null || currentGroup.hasRules()) {
                    currentGroup = new RobotsGroup();
                    groups.add(currentGroup);
                }

                currentGroup.addUserAgent(value);
                continue;
            }

            if (currentGroup == null) {
                continue;
            }

            if ("allow".equals(key) && !value.isBlank()) {
                currentGroup.addRule(new RobotsPathRule(true, value));
            }

            if ("disallow".equals(key) && !value.isBlank()) {
                currentGroup.addRule(new RobotsPathRule(false, value));
            }
        }

        RobotsGroup group = selectGroup(groups, userAgent);

        return new RobotsTxt(group.rules(), List.copyOf(sitemapUrls));
    }

    private RobotsGroup selectGroup(List<RobotsGroup> groups, String userAgent) {
        return groups.stream()
                .filter(group -> group.matches(userAgent))
                .max((left, right) -> Integer.compare(
                        left.specificity(userAgent),
                        right.specificity(userAgent)
                ))
                .orElseGet(RobotsGroup::new);
    }

    private String stripComment(String line) {
        int commentIndex = line.indexOf("#");

        if (commentIndex < 0) {
            return line;
        }

        return line.substring(0, commentIndex);
    }

    private static class RobotsGroup {

        private final List<String> userAgents = new ArrayList<>();
        private final List<RobotsPathRule> rules = new ArrayList<>();

        private void addUserAgent(String userAgent) {
            if (!userAgent.isBlank()) {
                userAgents.add(userAgent.toLowerCase(Locale.ROOT));
            }
        }

        private void addRule(RobotsPathRule rule) {
            rules.add(rule);
        }

        private boolean hasRules() {
            return !rules.isEmpty();
        }

        private List<RobotsPathRule> rules() {
            return List.copyOf(rules);
        }

        private boolean matches(String userAgent) {
            String normalizedUserAgent = normalizeUserAgent(userAgent);

            return userAgents.stream()
                    .anyMatch(candidate -> "*".equals(candidate)
                            || normalizedUserAgent.contains(candidate));
        }

        private int specificity(String userAgent) {
            String normalizedUserAgent = normalizeUserAgent(userAgent);

            return userAgents.stream()
                    .filter(candidate -> "*".equals(candidate)
                            || normalizedUserAgent.contains(candidate))
                    .mapToInt(candidate -> "*".equals(candidate) ? 0 : candidate.length())
                    .max()
                    .orElse(-1);
        }

        private String normalizeUserAgent(String userAgent) {
            return userAgent == null ? "" : userAgent.toLowerCase(Locale.ROOT);
        }
    }
}
