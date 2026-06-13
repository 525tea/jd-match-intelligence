package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectWorkflowFileAnalysisServiceTest {

    private final FixtureRepositoryFileClient repositoryFileClient =
            new FixtureRepositoryFileClient();

    private final ProjectWorkflowFileAnalysisService service =
            new ProjectWorkflowFileAnalysisService(repositoryFileClient);

    @Test
    @DisplayName("repository workflow 후보 경로를 조회해 experience tag 후보를 분석한다")
    void analyze() {
        RepositoryRef repositoryRef = RepositoryRef.of("example-org", "sample-repo");
        repositoryFileClient.put(
                repositoryRef,
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
                      - uses: actions/cache@v4
                      - run: ./gradlew :backend:test
                """
        );
        repositoryFileClient.put(
                repositoryRef,
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
                      - uses: docker/build-push-action@v6
                """
        );

        ProjectWorkflowFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of(
                        ".github/workflows/backend-ci.yml",
                        ".github/workflows/docker-image.yml",
                        ".github/workflows/README.md"
                )
        );

        assertThat(result.repositoryRef()).isEqualTo(repositoryRef);
        assertThat(result.requestedFileCount()).isEqualTo(3);
        assertThat(result.foundFileCount()).isEqualTo(2);
        assertThat(result.analyzedPaths())
                .containsExactly(
                        ".github/workflows/backend-ci.yml",
                        ".github/workflows/docker-image.yml"
                );
        assertThat(result.experienceTagCandidates())
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("CI_CD", "TESTING", "RELIABILITY", "PERFORMANCE", "CLOUD_INFRA", "SECURITY");
    }

    @Test
    @DisplayName("찾은 파일이 없으면 빈 분석 결과를 반환한다")
    void analyzeWithoutFiles() {
        RepositoryRef repositoryRef = RepositoryRef.of("example-org", "empty-repo");

        ProjectWorkflowFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of(".github/workflows/backend-ci.yml", ".github/workflows/docker-image.yml")
        );

        assertThat(result.repositoryRef()).isEqualTo(repositoryRef);
        assertThat(result.requestedFileCount()).isEqualTo(2);
        assertThat(result.foundFileCount()).isZero();
        assertThat(result.analyzedPaths()).isEmpty();
        assertThat(result.experienceTagCandidates()).isEmpty();
    }

    @Test
    @DisplayName("workflow 파일이 아닌 파일은 분석 대상에서 제외한다")
    void analyzeIgnoresNonWorkflowFiles() {
        RepositoryRef repositoryRef = RepositoryRef.of("example-org", "docs-repo");
        repositoryFileClient.put(repositoryRef, "README.md", "uses: actions/checkout@v5");
        repositoryFileClient.put(repositoryRef, ".github/workflows/README.md", "uses: actions/checkout@v5");

        ProjectWorkflowFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of("README.md", ".github/workflows/README.md")
        );

        assertThat(result.requestedFileCount()).isEqualTo(2);
        assertThat(result.foundFileCount()).isZero();
        assertThat(result.analyzedPaths()).isEmpty();
        assertThat(result.experienceTagCandidates()).isEmpty();
    }

    @Test
    @DisplayName("repository ref가 없으면 예외를 던진다")
    void analyzeWithoutRepositoryRef() {
        assertThatThrownBy(() -> service.analyze(null, List.of(".github/workflows/backend-ci.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repositoryRef must not be null");
    }

    private static class FixtureRepositoryFileClient implements RepositoryFileClient {

        private final Map<String, RepositoryFile> files = new HashMap<>();

        @Override
        public Optional<RepositoryFile> findFile(RepositoryRef repositoryRef, String path) {
            return Optional.ofNullable(files.get(key(repositoryRef, path)));
        }

        void put(
                RepositoryRef repositoryRef,
                String path,
                String content
        ) {
            files.put(key(repositoryRef, path), new RepositoryFile(path, content));
        }

        private String key(
                RepositoryRef repositoryRef,
                String path
        ) {
            return repositoryRef.fullName() + ":" + repositoryRef.ref() + ":" + path;
        }
    }
}
