package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfraFileTypeTest {

    @Test
    void detectInfraFileTypes() {
        assertThat(InfraFileType.fromPath("Dockerfile")).isEqualTo(InfraFileType.DOCKERFILE);
        assertThat(InfraFileType.fromPath("backend/Dockerfile.prod")).isEqualTo(InfraFileType.DOCKERFILE);
        assertThat(InfraFileType.fromPath("docker-compose.yml")).isEqualTo(InfraFileType.DOCKER_COMPOSE);
        assertThat(InfraFileType.fromPath("docker-compose.yaml")).isEqualTo(InfraFileType.DOCKER_COMPOSE);
        assertThat(InfraFileType.fromPath("compose.yml")).isEqualTo(InfraFileType.DOCKER_COMPOSE);
        assertThat(InfraFileType.fromPath("compose.yaml")).isEqualTo(InfraFileType.DOCKER_COMPOSE);
        assertThat(InfraFileType.fromPath("backend/src/main/resources/application.yml"))
                .isEqualTo(InfraFileType.APPLICATION_YAML);
        assertThat(InfraFileType.fromPath("backend/src/main/resources/application-local.yaml"))
                .isEqualTo(InfraFileType.APPLICATION_YAML);
        assertThat(InfraFileType.fromPath("backend/src/main/resources/application.properties"))
                .isEqualTo(InfraFileType.APPLICATION_PROPERTIES);
        assertThat(InfraFileType.fromPath("backend/src/main/resources/application-local.properties"))
                .isEqualTo(InfraFileType.APPLICATION_PROPERTIES);
        assertThat(InfraFileType.fromPath("README.md")).isEqualTo(InfraFileType.UNKNOWN);
        assertThat(InfraFileType.fromPath(null)).isEqualTo(InfraFileType.UNKNOWN);
    }
}
