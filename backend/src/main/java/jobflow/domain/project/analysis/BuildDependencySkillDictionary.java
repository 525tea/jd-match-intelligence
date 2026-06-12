package jobflow.domain.project.analysis;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class BuildDependencySkillDictionary {

    private static final List<SkillMapping> MAPPINGS = List.of(
            new SkillMapping("id:java", "Java", 0.90),
            new SkillMapping("plugin:java", "Java", 0.90),
            new SkillMapping("org.springframework.boot", "Spring Boot", 0.95),
            new SkillMapping("spring-boot-starter-web", "Spring Boot", 0.95),
            new SkillMapping("spring-boot-starter-webmvc", "Spring Boot", 0.95),
            new SkillMapping("spring-boot-starter-data-jpa", "Spring Data JPA", 0.95),
            new SkillMapping("spring-boot-starter-security", "Spring Security", 0.95),
            new SkillMapping("spring-boot-starter-oauth2-client", "OAuth2", 0.90),
            new SkillMapping("spring-boot-starter-validation", "Bean Validation", 0.85),
            new SkillMapping("spring-boot-starter-batch", "Spring Batch", 0.95),
            new SkillMapping("spring-boot-starter-actuator", "Spring Actuator", 0.90),
            new SkillMapping("spring-cloud-starter-gateway", "Spring Cloud Gateway", 0.95),
            new SkillMapping("spring-cloud-starter-netflix-eureka-client", "Eureka", 0.90),
            new SkillMapping("spring-kafka", "Kafka", 0.95),
            new SkillMapping("spring-boot-starter-data-redis", "Redis", 0.95),
            new SkillMapping("spring-boot-starter-data-elasticsearch", "Elasticsearch", 0.95),
            new SkillMapping("flyway-core", "Flyway", 0.95),
            new SkillMapping("flyway-mysql", "Flyway", 0.95),
            new SkillMapping("mysql-connector-j", "MySQL", 0.95),
            new SkillMapping("postgresql", "PostgreSQL", 0.95),
            new SkillMapping("mariadb-java-client", "MariaDB", 0.95),
            new SkillMapping("querydsl", "QueryDSL", 0.95),
            new SkillMapping("hibernate-core", "Hibernate", 0.90),
            new SkillMapping("jjwt", "JWT", 0.85),
            new SkillMapping("junit", "JUnit", 0.90),
            new SkillMapping("mockito", "Mockito", 0.85),
            new SkillMapping("lombok", "Lombok", 0.80),
            new SkillMapping("org.jetbrains.kotlin.jvm", "Kotlin", 0.95),
            new SkillMapping("kotlin-stdlib", "Kotlin", 0.95),
            new SkillMapping("@nestjs/core", "NestJS", 0.95),
            new SkillMapping("nestjs", "NestJS", 0.95),
            new SkillMapping("next", "Next.js", 0.95),
            new SkillMapping("react", "React", 0.95),
            new SkillMapping("react-native", "React Native", 0.95),
            new SkillMapping("typescript", "TypeScript", 0.95),
            new SkillMapping("express", "Express", 0.90),
            new SkillMapping("fastify", "Fastify", 0.90),
            new SkillMapping("node", "Node.js", 0.85),
            new SkillMapping("prisma", "Prisma", 0.90),
            new SkillMapping("typeorm", "TypeORM", 0.90),
            new SkillMapping("tailwindcss", "Tailwind CSS", 0.90),
            new SkillMapping("vite", "Vite", 0.85),
            new SkillMapping("webpack", "Webpack", 0.85),
            new SkillMapping("eslint", "ESLint", 0.80),
            new SkillMapping("jest", "Jest", 0.90)
    );

    private BuildDependencySkillDictionary() {
    }

    static List<BuildFileSkillCandidate> match(String evidence) {
        if (evidence == null || evidence.isBlank()) {
            return List.of();
        }

        String normalizedEvidence = normalize(evidence);
        return MAPPINGS.stream()
                .filter(mapping -> normalizedEvidence.contains(normalize(mapping.token())))
                .map(mapping -> BuildFileSkillCandidate.of(
                        mapping.skillName(),
                        evidence,
                        mapping.confidence()
                ))
                .sorted(Comparator.comparing(BuildFileSkillCandidate::skillName))
                .toList();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .trim();
    }

    private record SkillMapping(
            String token,
            String skillName,
            double confidence
    ) {
    }
}
