package jobflow.domain.project.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class WorkflowEvidenceDictionary {

    private static final List<Mapping> MAPPINGS = List.of(
            new Mapping("CI_CD", 0.95,
                    "github actions", "actions/checkout", "workflow_dispatch", "pull_request", "push:",
                    "uses:", "jobs:", "steps:", "runs-on:"),
            new Mapping("CI_CD", 0.90,
                    "gradle test", "./gradlew", "mvn test", "npm test", "pnpm test", "yarn test",
                    "docker/build-push-action", "docker/login-action", "docker/setup-buildx-action"),
            new Mapping("TESTING", 0.85,
                    "test", "junit", "pytest", "jest", "vitest", "playwright", "cypress",
                    "jacoco", "coverage", "testcontainers"),
            new Mapping("CLOUD_INFRA", 0.85,
                    "docker", "container", "buildx", "image", "registry", "dockerhub",
                    "aws-actions", "azure", "gcp", "s3", "ecr", "eks", "kubernetes", "kubectl", "helm"),
            new Mapping("RELIABILITY", 0.75,
                    "cache", "actions/cache", "timeout-minutes", "retry", "matrix:", "strategy:",
                    "concurrency:", "needs:"),
            new Mapping("SECURITY", 0.80,
                    "permissions:", "secrets.", "oidc", "id-token", "codeql", "dependency-review",
                    "trivy", "snyk", "sonarqube", "secret scanning"),
            new Mapping("PERFORMANCE", 0.65,
                    "gradle cache", "npm cache", "pnpm cache", "yarn cache", "setup-java",
                    "setup-node", "cache-dependency-path")
    );

    private WorkflowEvidenceDictionary() {
    }

    static List<WorkflowExperienceTagCandidate> match(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return List.of();
        }

        String normalizedEvidence = evidence.toLowerCase(Locale.ROOT);
        List<WorkflowExperienceTagCandidate> candidates = new ArrayList<>();

        for (Mapping mapping : MAPPINGS) {
            if (mapping.matches(normalizedEvidence)) {
                candidates.add(WorkflowExperienceTagCandidate.of(
                        mapping.tagCode(),
                        mapping.confidence(),
                        evidence
                ));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparing(WorkflowExperienceTagCandidate::tagCode))
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
