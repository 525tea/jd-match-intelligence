package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowFileTypeTest {

    @Test
    void detectGitHubActionsWorkflowFileTypes() {
        assertThat(WorkflowFileType.fromPath(".github/workflows/backend-ci.yml"))
                .isEqualTo(WorkflowFileType.GITHUB_ACTIONS);
        assertThat(WorkflowFileType.fromPath(".github/workflows/docker-image.yaml"))
                .isEqualTo(WorkflowFileType.GITHUB_ACTIONS);
        assertThat(WorkflowFileType.fromPath("repo/.github/workflows/deploy.yml"))
                .isEqualTo(WorkflowFileType.GITHUB_ACTIONS);
        assertThat(WorkflowFileType.fromPath(".github/workflows/README.md"))
                .isEqualTo(WorkflowFileType.UNKNOWN);
        assertThat(WorkflowFileType.fromPath("backend/build.gradle"))
                .isEqualTo(WorkflowFileType.UNKNOWN);
        assertThat(WorkflowFileType.fromPath(null))
                .isEqualTo(WorkflowFileType.UNKNOWN);
        assertThat(WorkflowFileType.fromPath(""))
                .isEqualTo(WorkflowFileType.UNKNOWN);
    }
}
