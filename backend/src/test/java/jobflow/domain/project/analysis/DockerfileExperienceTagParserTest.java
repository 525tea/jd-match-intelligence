package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DockerfileExperienceTagParserTest {

    private final DockerfileExperienceTagParser parser = new DockerfileExperienceTagParser();

    @Test
    void parseDockerfileExperienceTags() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath(
                "Dockerfile",
                """
                FROM eclipse-temurin:21-jre
                HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health
                ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus
                """
        );

        assertThat(parser.parse(infraFile))
                .extracting(InfraExperienceTagCandidate::tagCode)
                .contains("CLOUD_INFRA", "RELIABILITY", "MONITORING");
    }

    @Test
    void ignoreBlankDockerfile() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath("Dockerfile", " ");

        assertThat(parser.parse(infraFile)).isEmpty();
    }
}
