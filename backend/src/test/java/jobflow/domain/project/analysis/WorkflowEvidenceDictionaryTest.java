package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowEvidenceDictionaryTest {

    @Test
    void matchCiCdEvidence() {
        assertThat(WorkflowEvidenceDictionary.match(".github/workflows/backend-ci.yml: uses: actions/checkout@v5"))
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("CI_CD");
    }

    @Test
    void matchTestingEvidence() {
        assertThat(WorkflowEvidenceDictionary.match(".github/workflows/backend-ci.yml: run: ./gradlew :backend:test"))
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("CI_CD", "TESTING");
    }

    @Test
    void matchCloudInfraEvidence() {
        assertThat(WorkflowEvidenceDictionary.match(".github/workflows/docker.yml: uses: docker/build-push-action@v6"))
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("CI_CD", "CLOUD_INFRA");
    }

    @Test
    void matchSecurityEvidence() {
        assertThat(WorkflowEvidenceDictionary.match(".github/workflows/codeql.yml: permissions: id-token: write"))
                .extracting(WorkflowExperienceTagCandidate::tagCode)
                .contains("SECURITY");
    }

    @Test
    void returnEmptyWhenEvidenceIsBlank() {
        assertThat(WorkflowEvidenceDictionary.match("")).isEmpty();
        assertThat(WorkflowEvidenceDictionary.match(null)).isEmpty();
    }
}
