package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProjectBuildFileAnalysisServiceTest {

    private final FixtureRepositoryFileClient repositoryFileClient =
            new FixtureRepositoryFileClient();

    private final ProjectBuildFileAnalysisService service =
            new ProjectBuildFileAnalysisService(repositoryFileClient);

    @Test
    @DisplayName("repository build file 후보 경로를 조회해 skill 후보를 분석한다")
    void analyze() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "jobflow");
        repositoryFileClient.put(
                repositoryRef,
                "backend/build.gradle",
                """
                plugins {
                    id 'java'
                    id 'org.springframework.boot' version '4.0.6'
                }

                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
                    implementation 'org.springframework.boot:spring-boot-starter-security'
                    runtimeOnly 'com.mysql:mysql-connector-j'
                }
                """
        );
        repositoryFileClient.put(
                repositoryRef,
                "frontend/package.json",
                """
                {
                  "dependencies": {
                    "next": "15.0.0",
                    "react": "19.0.0",
                    "typescript": "5.0.0"
                  }
                }
                """
        );

        ProjectBuildFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of("backend/build.gradle", "frontend/package.json", "README.md")
        );

        assertThat(result.repositoryRef()).isEqualTo(repositoryRef);
        assertThat(result.requestedFileCount()).isEqualTo(3);
        assertThat(result.foundFileCount()).isEqualTo(2);
        assertThat(result.analyzedPaths())
                .containsExactly("backend/build.gradle", "frontend/package.json");
        assertThat(result.skillCandidates())
                .extracting(BuildFileSkillCandidate::skillName)
                .containsExactly(
                        "Java",
                        "MySQL",
                        "Next.js",
                        "React",
                        "Spring Boot",
                        "Spring Data JPA",
                        "Spring Security",
                        "TypeScript"
                );
    }

    @Test
    @DisplayName("찾은 파일이 없으면 빈 분석 결과를 반환한다")
    void analyzeWithoutFiles() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "empty");

        ProjectBuildFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of("build.gradle", "pom.xml", "package.json")
        );

        assertThat(result.repositoryRef()).isEqualTo(repositoryRef);
        assertThat(result.requestedFileCount()).isEqualTo(3);
        assertThat(result.foundFileCount()).isZero();
        assertThat(result.analyzedPaths()).isEmpty();
        assertThat(result.skillCandidates()).isEmpty();
    }

    @Test
    @DisplayName("빌드 파일이 아닌 파일은 분석 대상에서 제외한다")
    void analyzeIgnoresNonBuildFiles() {
        RepositoryRef repositoryRef = RepositoryRef.of("525tea", "docs");
        repositoryFileClient.put(repositoryRef, "README.md", "# Spring Boot React");

        ProjectBuildFileAnalysisResult result = service.analyze(
                repositoryRef,
                List.of("README.md")
        );

        assertThat(result.requestedFileCount()).isEqualTo(1);
        assertThat(result.foundFileCount()).isZero();
        assertThat(result.analyzedPaths()).isEmpty();
        assertThat(result.skillCandidates()).isEmpty();
    }

    @Test
    @DisplayName("repository ref가 없으면 예외를 던진다")
    void analyzeWithoutRepositoryRef() {
        assertThatThrownBy(() -> service.analyze(null, List.of("build.gradle")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repositoryRef must not be null");
    }

    private static class FixtureRepositoryFileClient implements RepositoryFileClient {

        private final Map<String, RepositoryFile> files = new HashMap<>();

        @Override
        public Optional<RepositoryFile> findFile(RepositoryRef repositoryRef, String path) {
            return Optional.ofNullable(files.get(key(repositoryRef, path)));
        }

        void put(
                RepositoryRef repositoryRef,
                String path,
                String content
        ) {
            files.put(key(repositoryRef, path), new RepositoryFile(path, content));
        }

        private String key(
                RepositoryRef repositoryRef,
                String path
        ) {
            return repositoryRef.fullName() + ":" + repositoryRef.ref() + ":" + path;
        }
    }
}
