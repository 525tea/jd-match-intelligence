package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApplicationConfigExperienceTagParserTest {

    private final ApplicationConfigExperienceTagParser parser = new ApplicationConfigExperienceTagParser();

    @Test
    void parseApplicationYamlExperienceTagsWithoutLeakingSensitiveLines() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath(
                "application.yml",
                """
                spring:
                  datasource:
                    url: jdbc:mysql://localhost:3306/jobflow
                    password: should-not-be-evidence
                  data:
                    redis:
                      host: localhost
                  elasticsearch:
                    uris: http://localhost:9200
                management:
                  endpoints:
                    web:
                      exposure:
                        include: health,metrics,prometheus
                """
        );

        assertThat(parser.parse(infraFile))
                .extracting(InfraExperienceTagCandidate::tagCode)
                .contains("PERFORMANCE", "SCALABILITY", "MONITORING");

        assertThat(parser.parse(infraFile))
                .extracting(InfraExperienceTagCandidate::evidence)
                .noneMatch(evidence -> evidence.contains("should-not-be-evidence"));
    }

    @Test
    void parseApplicationPropertiesExperienceTags() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath(
                "application-local.properties",
                """
                spring.data.redis.host=localhost
                spring.kafka.bootstrap-servers=localhost:9092
                management.endpoints.web.exposure.include=health,metrics,prometheus
                app.github.client-secret=should-not-be-evidence
                """
        );

        assertThat(parser.parse(infraFile))
                .extracting(InfraExperienceTagCandidate::tagCode)
                .contains("PERFORMANCE", "SCALABILITY", "MONITORING");

        assertThat(parser.parse(infraFile))
                .extracting(InfraExperienceTagCandidate::evidence)
                .noneMatch(evidence -> evidence.contains("should-not-be-evidence"));
    }

    @Test
    void ignoreBlankApplicationConfig() {
        RepositoryInfraFile infraFile = RepositoryInfraFile.fromPath("application.yml", "");

        assertThat(parser.parse(infraFile)).isEmpty();
    }
}
