package jobflow.domain.project.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BuildFileTypeTest {

    @Test
    @DisplayName("빌드 파일 경로로 파일 타입을 판별한다")
    void fromPath() {
        assertThat(BuildFileType.fromPath("backend/build.gradle")).isEqualTo(BuildFileType.GRADLE);
        assertThat(BuildFileType.fromPath("backend/build.gradle.kts")).isEqualTo(BuildFileType.GRADLE);
        assertThat(BuildFileType.fromPath("pom.xml")).isEqualTo(BuildFileType.MAVEN);
        assertThat(BuildFileType.fromPath("frontend/package.json")).isEqualTo(BuildFileType.PACKAGE_JSON);
        assertThat(BuildFileType.fromPath("README.md")).isEqualTo(BuildFileType.UNKNOWN);
    }
}
