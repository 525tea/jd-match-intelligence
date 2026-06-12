package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RepositoryRefTest {

    @Test
    @DisplayName("owner/name/ref로 repository 식별자를 만든다")
    void create() {
        RepositoryRef repositoryRef = new RepositoryRef("525tea", "jobflow", "main");

        assertThat(repositoryRef.owner()).isEqualTo("525tea");
        assertThat(repositoryRef.name()).isEqualTo("jobflow");
        assertThat(repositoryRef.ref()).isEqualTo("main");
        assertThat(repositoryRef.fullName()).isEqualTo("525tea/jobflow");
    }

    @Test
    @DisplayName("ref가 비어 있으면 HEAD를 기본값으로 사용한다")
    void createWithDefaultRef() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");

        assertThat(repositoryRef.ref()).isEqualTo("HEAD");
    }

    @Test
    @DisplayName("owner나 name이 비어 있으면 예외를 던진다")
    void createWithoutRequiredValues() {
        assertThatThrownBy(() -> new RepositoryRef("", "jobflow", "main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("owner must not be blank");

        assertThatThrownBy(() -> new RepositoryRef("525tea", "", "main"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }
}
