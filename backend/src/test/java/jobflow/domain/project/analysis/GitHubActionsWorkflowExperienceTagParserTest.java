package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GitHubActionsWorkflowExperienceTagParserTest {

    private final GitHubActionsWorkflowExperienceTagParser parser =
            new GitHubActionsWorkflowExperienceTagParser();

    @Test
    void parseGitHubActionsWorkflowExperienceTags() {
        RepositoryWorkflowFile workflowFile = RepositoryWorkflowFile.fromPath(
                ".github/workflows/backend-ci.yml",
                """
                name: Backend CI

                on:
                  pull_request:
                  push:
                    branches: [main]

                jobs:
                  backend-test:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v5
                      - uses: actions/setup-java@v5
                        with:
                          distribution: temurin
                          java-version: 21
                      - uses: actions/cache@v4
                        with:
                          path: ~/.gradle/caches
                      - run: ./gradlew :backend:test
                """
        );

        assertThat(parser.parse(workflowFile))
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("CI_CD", "TESTING", "RELIABILITY", "PERFORMANCE");
    }

    @Test
    void parseDockerWorkflowExperienceTags() {
        RepositoryWorkflowFile workflowFile = RepositoryWorkflowFile.fromPath(
                ".github/workflows/docker-image.yml",
                """
                name: Docker Image

                on:
                  push:
                    branches: [main]

                jobs:
                  docker:
                    runs-on: ubuntu-latest
                    permissions:
                      contents: read
                      packages: write
                    steps:
                      - uses: actions/checkout@v5
                      - uses: docker/setup-buildx-action@v3
                      - uses: docker/login-action@v3
                      - uses: docker/build-push-action@v6
                        with:
                          push: true
                """
        );

        assertThat(parser.parse(workflowFile))
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("CI_CD", "CLOUD_INFRA", "SECURITY");
    }

    @Test
    void skipSensitiveLiteralValuesButKeepSecretReferenceShape() {
        RepositoryWorkflowFile workflowFile = RepositoryWorkflowFile.fromPath(
                ".github/workflows/deploy.yml",
                """
                jobs:
                  deploy:
                    steps:
                      - run: echo "${{ secrets.DEPLOY_TOKEN }}"
                      - run: echo "token: plain-text-token"
                      - run: echo "password: plain-text-password"
                """
        );

        assertThat(parser.parse(workflowFile))
                .extracting(WorkflowExperienceTagCandidate::evidence)
                .anyMatch(evidence -> evidence.contains("${{ secrets.DEPLOY_TOKEN }}"))
                .noneMatch(evidence -> evidence.contains("plain-text-token"))
                .noneMatch(evidence -> evidence.contains("plain-text-password"));
    }

    @Test
    void returnEmptyWhenWorkflowFileIsUnsupportedOrBlank() {
        assertThat(parser.parse(RepositoryWorkflowFile.fromPath("README.md", "name: test"))).isEmpty();
        assertThat(parser.parse(RepositoryWorkflowFile.fromPath(".github/workflows/ci.yml", ""))).isEmpty();
    }
}
