package jobflow.domain.project.analysis;

import java.util.Locale;

public enum InfraFileType {

    DOCKERFILE,
    DOCKER_COMPOSE,
    APPLICATION_YAML,
    APPLICATION_PROPERTIES,
    UNKNOWN;

    public static InfraFileType fromPath(String path) {
        if (path == null || path.isBlank()) {
            return UNKNOWN;
        }

        String normalizedPath = path.toLowerCase(Locale.ROOT);
        String fileName = normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);

        if (fileName.equals("dockerfile") || fileName.startsWith("dockerfile.")) {
            return DOCKERFILE;
        }
        if (fileName.equals("docker-compose.yml")
                || fileName.equals("docker-compose.yaml")
                || fileName.equals("compose.yml")
                || fileName.equals("compose.yaml")) {
            return DOCKER_COMPOSE;
        }
        if (fileName.equals("application.yml")
                || fileName.equals("application.yaml")
                || (fileName.startsWith("application-") && (fileName.endsWith(".yml") || fileName.endsWith(".yaml")))) {
            return APPLICATION_YAML;
        }
        if (fileName.equals("application.properties")
                || (fileName.startsWith("application-") && fileName.endsWith(".properties"))) {
            return APPLICATION_PROPERTIES;
        }

        return UNKNOWN;
    }
}
