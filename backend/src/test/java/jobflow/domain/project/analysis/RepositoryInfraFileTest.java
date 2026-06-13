package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepositoryInfraFileTest {

    @Test
    void createRepositoryInfraFileFromPath() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath(
                "backend/Dockerfile",
                "FROM eclipse-temurin:21"
        );

        assertThat(infraFile.path()).isEqualTo("backend/Dockerfile");
        assertThat(infraFile.type()).isEqualTo(InfraFileType.DOCKERFILE);
        assertThat(infraFile.content()).isEqualTo("FROM eclipse-temurin:21");
    }

    @Test
    void replaceNullContentWithEmptyString() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath("docker-compose.yml", null);

        assertThat(infraFile.type()).isEqualTo(InfraFileType.DOCKER_COMPOSE);
        assertThat(infraFile.content()).isEmpty();
    }
}
