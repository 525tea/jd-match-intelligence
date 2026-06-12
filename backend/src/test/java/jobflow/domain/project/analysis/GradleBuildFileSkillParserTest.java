package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GradleBuildFileSkillParserTest {

    private final GradleBuildFileSkillParser parser = new GradleBuildFileSkillParser();

    @Test
    @DisplayName("Gradle plugin과 dependency에서 skill 후보를 추출한다")
    void parse() {
        RepositoryBuildFile buildFile = RepositoryBuildFile.fromPath(
                "backend/build.gradle",
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '4.0.6'
                }

                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    implementation 'org.springframework.boot:spring-boot-starter-security'
                    implementation 'org.flywaydb:flyway-mysql'
                    runtimeOnly 'com.mysql:mysql-connector-j'
                    testImplementation 'org.junit.jupiter:junit-jupiter'
                }
                """
        );

        List<BuildFileSkillCandidate> candidates = parser.parse(buildFile);

        assertThat(candidates)
                .extracting(BuildFileSkillCandidate::skillName)
                .contains(
                        "Java",
                        "Spring Boot",
                        "Spring Data JPA",
                        "Spring Security",
                        "Flyway",
                        "MySQL",
                        "JUnit"
                );
    }

    @Test
    @DisplayName("빈 Gradle 파일이면 skill 후보를 반환하지 않는다")
    void parseBlankFile() {
        RepositoryBuildFile buildFile = RepositoryBuildFile.fromPath("build.gradle", " ");

        assertThat(parser.parse(buildFile)).isEmpty();
    }
}
