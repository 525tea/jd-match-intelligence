package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildFileSkillAnalyzerTest {

    private final BuildFileSkillAnalyzer analyzer = BuildFileSkillAnalyzer.defaultAnalyzer();

    @Test
    @DisplayName("여러 빌드 파일에서 skill 후보를 추출하고 중복 skill을 제거한다")
    void analyze() {
        List<RepositoryBuildFile> buildFiles = List.of(
                RepositoryBuildFile.fromPath(
                        "backend/build.gradle",
                        """
                        plugins {
                            id 'java'
                            id 'org.springframework.boot' version '4.0.6'
                        }

                        dependencies {
                            implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                            runtimeOnly 'com.mysql:mysql-connector-j'
                        }
                        """
                ),
                RepositoryBuildFile.fromPath(
                        "frontend/package.json",
                        """
                        {
                          "dependencies": {
                            "react": "19.0.0",
                            "next": "15.0.0",
                            "typescript": "5.0.0"
                          }
                        }
                        """
                ),
                RepositoryBuildFile.fromPath(
                        "README.md",
                        "# ignored"
                )
        );

        List<BuildFileSkillCandidate> candidates = analyzer.analyze(buildFiles);

        assertThat(candidates)
                .extracting(BuildFileSkillCandidate::skillName)
                .containsExactly(
                        "Java",
                        "MySQL",
                        "Next.js",
                        "React",
                        "Spring Boot",
                        "Spring Data JPA",
                        "TypeScript"
                );
    }

    @Test
    @DisplayName("입력 빌드 파일이 없으면 빈 skill 후보를 반환한다")
    void analyzeEmptyFiles() {
        assertThat(analyzer.analyze(List.of())).isEmpty();
        assertThat(analyzer.analyze(null)).isEmpty();
    }
}
