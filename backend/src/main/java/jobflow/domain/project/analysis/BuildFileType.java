package jobflow.domain.project.analysis;

import java.util.Locale;

public enum BuildFileType {

    GRADLE,
    MAVEN,
    PACKAGE_JSON,
    UNKNOWN;

    public static BuildFileType fromPath(String path) {
        if (path == null || path.isBlank()) {
            return UNKNOWN;
        }

        String normalizedPath = path.toLowerCase(Locale.ROOT);
        if (normalizedPath.endsWith("build.gradle")
                || normalizedPath.endsWith("build.gradle.kts")) {
            return GRADLE;
        }
        if (normalizedPath.endsWith("pom.xml")) {
            return MAVEN;
        }
        if (normalizedPath.endsWith("package.json")) {
            return PACKAGE_JSON;
        }
        return UNKNOWN;
    }
}
