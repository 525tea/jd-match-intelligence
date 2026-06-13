package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class InfraEvidenceDictionary {

    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping("CLOUD_INFRA", 0.90, "kubernetes", "k8s", "eks", "gke", "aks", "helm"),
            new Mapping("CLOUD_INFRA", 0.85, "aws", "amazonaws", "s3", "ec2", "rds", "cloudfront", "route53"),
            new Mapping("CLOUD_INFRA", 0.80, "docker", "dockerfile", "docker compose", "docker-compose", "compose.yml"),
            new Mapping("CI_CD", 0.75, "jenkins", "github actions", "gitlab ci", "circleci", "argocd"),
            new Mapping("MONITORING", 0.85, "prometheus", "grafana", "loki", "jaeger", "zipkin", "opentelemetry", "actuator"),
            new Mapping("MONITORING", 0.70, "logging", "metrics", "management.endpoints"),
            new Mapping("RELIABILITY", 0.80, "healthcheck", "restart:", "readiness", "liveness", "depends_on"),
            new Mapping("RELIABILITY", 0.70, "replica", "replicas", "timeout", "retry", "resilience4j"),
            new Mapping("PERFORMANCE", 0.75, "redis", "cache", "caffeine", "hikari", "connection-pool"),
            new Mapping("PERFORMANCE", 0.65, "nginx", "load balancer", "load-balancer"),
            new Mapping("SCALABILITY", 0.80, "kafka", "rabbitmq", "sqs", "pubsub", "message broker"),
            new Mapping("SCALABILITY", 0.70, "elasticsearch", "opensearch", "mongodb", "postgres", "mysql"),
            new Mapping("SECURITY", 0.75, "oauth2", "jwt", "spring.security", "ssl", "tls", "keystore", "truststore"),
            new Mapping("TESTING", 0.70, "testcontainers", "wiremock", "mockserver")
    );

    private InfraEvidenceDictionary() {
    }

    static List<InfraExperienceTagCandidate> match(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return List.of();
        }

        String normalizedEvidence = evidence.toLowerCase(Locale.ROOT);
        List<InfraExperienceTagCandidate> candidates = new ArrayList<>();

        for (Mapping mapping : MAPPINGS) {
            if (mapping.matches(normalizedEvidence)) {
                candidates.add(InfraExperienceTagCandidate.of(
                        mapping.tagCode(),
                        mapping.confidence(),
                        evidence
                ));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparing(InfraExperienceTagCandidate::tagCode))
                .toList();
    }

    private record Mapping(
            String tagCode,
            double confidence,
            List<String> keywords
    ) {

        private Mapping(String tagCode, double confidence, String... keywords) {
            this(tagCode, confidence, List.of(keywords));
        }

        private boolean matches(String normalizedEvidence) {
            return keywords.stream().anyMatch(normalizedEvidence::contains);
        }
    }
}
