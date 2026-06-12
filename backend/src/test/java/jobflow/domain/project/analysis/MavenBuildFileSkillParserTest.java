package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MavenBuildFileSkillParserTest {

    private final MavenBuildFileSkillParser parser = new MavenBuildFileSkillParser();

    @Test
    @DisplayName("Maven dependency와 plugin에서 skill 후보를 추출한다")
    void parse() {
        RepositoryBuildFile buildFile = RepositoryBuildFile.fromPath(
                "pom.xml",
                """
                <project>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-security</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """
        );

        List<BuildFileSkillCandidate> candidates = parser.parse(buildFile);

        assertThat(candidates)
                .extracting(BuildFileSkillCandidate::skillName)
                .contains("Spring Boot", "Spring Security", "PostgreSQL");
    }

    @Test
    @DisplayName("파싱할 수 없는 Maven XML이면 빈 skill 후보를 반환한다")
    void parseInvalidXml() {
        RepositoryBuildFile buildFile = RepositoryBuildFile.fromPath("pom.xml", "<project>");

        assertThat(parser.parse(buildFile)).isEmpty();
    }
}
