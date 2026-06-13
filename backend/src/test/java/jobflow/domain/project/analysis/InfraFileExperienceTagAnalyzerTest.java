package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InfraFileExperienceTagAnalyzerTest {

    private final InfraFileExperienceTagAnalyzer analyzer = InfraFileExperienceTagAnalyzer.defaultAnalyzer();

    @Test
    void analyzeInfraFilesAndDeduplicateByTagCode() {
        List<RepositoryInfraFile> infraFiles = List.of(
                RepositoryInfraFile.fromPath(
                        "Dockerfile",
                        """
                        FROM eclipse-temurin:21-jre
                        HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health
                        """
                ),
                RepositoryInfraFile.fromPath(
                        "docker-compose.yml",
                        """
                        services:
                          redis:
                            image: redis:7.4
                          kafka:
                            image: apache/kafka:latest
                        """
                ),
                RepositoryInfraFile.fromPath(
                        "application.yml",
                        """
                        spring:
                          kafka:
                            bootstrap-servers: localhost:9092
                        management.endpoints.web.exposure.include: health,metrics,prometheus
                        """
                )
        );

        List<InfraExperienceTagCandidate> candidates = analyzer.analyze(infraFiles);

        assertThat(candidates)
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
    void keepHigherConfidenceWhenSameTagAppearsMultipleTimes() {
        List<RepositoryInfraFile> infraFiles = List.of(
                RepositoryInfraFile.fromPath(
                        "Dockerfile",
                        "FROM eclipse-temurin:21-jre"
                ),
                RepositoryInfraFile.fromPath(
                        "docker-compose.yml",
                        """
                        services:
                          app:
                            image: jobflow/backend
                        """
                )
        );

        List<InfraExperienceTagCandidate> candidates = analyzer.analyze(infraFiles);

        assertThat(candidates)
                .filteredOn(candidate -> candidate.tagCode().equals("CLOUD_INFRA"))
                .singleElement()
                .satisfies(candidate -> {
                    assertThat(candidate.confidence()).isEqualByComparingTo("0.85");
                    assertThat(candidate.evidence()).contains("docker compose local infrastructure orchestration");
                });
    }

    @Test
    void ignoreUnknownFiles() {
        List<InfraExperienceTagCandidate> candidates = analyzer.analyze(List.of(
                RepositoryInfraFile.fromPath("README.md", "redis kafka docker")
        ));

        assertThat(candidates).isEmpty();
    }

    @Test
    void returnEmptyCandidatesForEmptyInput() {
        assertThat(analyzer.analyze(List.of())).isEmpty();
        assertThat(analyzer.analyze(null)).isEmpty();
    }
}
