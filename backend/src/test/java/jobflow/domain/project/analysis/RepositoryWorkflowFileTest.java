package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepositoryWorkflowFileTest {

    @Test
    void createRepositoryWorkflowFileFromPath() {
        RepositoryWorkflowFile workflowFile = RepositoryWorkflowFile.fromPath(
                ".github/workflows/backend-ci.yml",
                "name: Backend CI"
        );

        assertThat(workflowFile.path()).isEqualTo(".github/workflows/backend-ci.yml");
        assertThat(workflowFile.type()).isEqualTo(WorkflowFileType.GITHUB_ACTIONS);
        assertThat(workflowFile.content()).isEqualTo("name: Backend CI");
        assertThat(workflowFile.isSupported()).isTrue();
    }

    @Test
    void replaceNullContentWithEmptyString() {
        RepositoryWorkflowFile workflowFile = RepositoryWorkflowFile.fromPath(
                ".github/workflows/backend-ci.yml",
                null
        );

        assertThat(workflowFile.type()).isEqualTo(WorkflowFileType.GITHUB_ACTIONS);
        assertThat(workflowFile.content()).isEmpty();
        assertThat(workflowFile.isSupported()).isTrue();
    }

    @Test
    void markUnsupportedFile() {
        RepositoryWorkflowFile workflowFile = RepositoryWorkflowFile.fromPath(
                "README.md",
                "# JobFlow"
        );

        assertThat(workflowFile.type()).isEqualTo(WorkflowFileType.UNKNOWN);
        assertThat(workflowFile.isSupported()).isFalse();
    }
}
