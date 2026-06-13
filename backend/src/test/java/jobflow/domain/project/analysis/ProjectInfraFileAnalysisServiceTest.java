package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectInfraFileAnalysisServiceTest {

    private final FixtureRepositoryFileClient repositoryFileClient =
            new FixtureRepositoryFileClient();

    private final ProjectInfraFileAnalysisService service =
            new ProjectInfraFileAnalysisService(repositoryFileClient);

    @Test
    @DisplayName("repository infra file 후보 경로를 조회해 experience tag 후보를 분석한다")
    void analyze() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");
        repositoryFileClient.put(
                repositoryRef,
                "Dockerfile",
                """
                FROM eclipse-temurin:21-jre
                HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health
                """
        );
        repositoryFileClient.put(
                repositoryRef,
                "docker-compose.yml",
                """
                services:
                  redis:
                    image: redis:7.4
                  kafka:
                    image: apache/kafka:latest
                """
        );
        repositoryFileClient.put(
                repositoryRef,
                "backend/src/main/resources/application.yml",
                """
                spring:
                  elasticsearch:
                    uris: http://localhost:9200
                management.endpoints.web.exposure.include: health,metrics,prometheus
                """
        );

        ProjectInfraFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of(
                        "Dockerfile",
                        "docker-compose.yml",
                        "backend/src/main/resources/application.yml",
                        "README.md"
                )
        );

        assertThat(result.repositoryRef()).isEqualTo(repositoryRef);
        assertThat(result.requestedFileCount()).isEqualTo(4);
        assertThat(result.foundFileCount()).isEqualTo(3);
        assertThat(result.analyzedPaths())
                .containsExactly(
                        "Dockerfile",
                        "docker-compose.yml",
                        "backend/src/main/resources/application.yml"
                );
        assertThat(result.experienceTagCandidates())
                .extracting(InfraExperienceTagCandidate::tagCode)
                .containsExactly(
                        "CLOUD_INFRA",
                        "MONITORING",
                        "PERFORMANCE",
                        "RELIABILITY",
                        "SCALABILITY"
                );
    }

    @Test
    @DisplayName("찾은 파일이 없으면 빈 분석 결과를 반환한다")
    void analyzeWithoutFiles() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "empty");

        ProjectInfraFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of("Dockerfile", "docker-compose.yml", "application.yml")
        );

        assertThat(result.repositoryRef()).isEqualTo(repositoryRef);
        assertThat(result.requestedFileCount()).isEqualTo(3);
        assertThat(result.foundFileCount()).isZero();
        assertThat(result.analyzedPaths()).isEmpty();
        assertThat(result.experienceTagCandidates()).isEmpty();
    }

    @Test
    @DisplayName("인프라 파일이 아닌 파일은 분석 대상에서 제외한다")
    void analyzeIgnoresNonInfraFiles() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "docs");
        repositoryFileClient.put(repositoryRef, "README.md", "# Redis Kafka Docker");

        ProjectInfraFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of("README.md")
        );

        assertThat(result.requestedFileCount()).isEqualTo(1);
        assertThat(result.foundFileCount()).isZero();
        assertThat(result.analyzedPaths()).isEmpty();
        assertThat(result.experienceTagCandidates()).isEmpty();
    }

    @Test
    @DisplayName("repository ref가 없으면 예외를 던진다")
    void analyzeWithoutRepositoryRef() {
        assertThatThrownBy(() -> service.analyze(null, List.of("Dockerfile")))
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
