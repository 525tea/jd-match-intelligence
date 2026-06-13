package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InfraEvidenceDictionaryTest {

    @Test
    void matchInfraEvidenceToExperienceTags() {
        assertThat(InfraEvidenceDictionary.match("docker-compose.yml: image: redis:7.4"))
                .extracting(InfraExperienceTagCandidate::tagCode)
                .contains("CLOUD_INFRA", "PERFORMANCE");

        assertThat(InfraEvidenceDictionary.match("application.yml: spring.kafka.bootstrap-servers=localhost:9092"))
                .extracting(InfraExperienceTagCandidate::tagCode)
                .contains("SCALABILITY");
    }

    @Test
    void returnEmptyCandidatesForBlankEvidence() {
        assertThat(InfraEvidenceDictionary.match(" ")).isEmpty();
    }
}
