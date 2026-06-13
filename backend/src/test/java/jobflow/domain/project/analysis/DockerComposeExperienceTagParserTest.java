package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DockerComposeExperienceTagParserTest {

    private final DockerComposeExperienceTagParser parser = new DockerComposeExperienceTagParser();

    @Test
    void parseDockerComposeExperienceTags() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath(
                "docker-compose.yml",
                """
                services:
                  mysql:
                    image: mysql:8.4
                    healthcheck:
                      test: ["CMD", "mysqladmin", "ping"]
                  redis:
                    image: redis:7.4
                  elasticsearch:
                    image: elasticsearch:8.17.0
                    restart: unless-stopped
                """
        );

        assertThat(parser.parse(infraFile))
                .extracting(InfraExperienceTagCandidate::tagCode)
                .contains("CLOUD_INFRA", "RELIABILITY", "PERFORMANCE", "SCALABILITY");
    }

    @Test
    void ignoreBlankDockerCompose() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath("docker-compose.yml", "");

        assertThat(parser.parse(infraFile)).isEmpty();
    }
}
