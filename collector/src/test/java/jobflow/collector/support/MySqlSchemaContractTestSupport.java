package jobflow.collector.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class MySqlSchemaContractTestSupport {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("jobflow_schema_contract")
            .withUsername("jobflow")
            .withPassword("jobflow");

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", MySqlSchemaContractTestSupport::backendMigrationLocation);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");

        registry.add("app.collector.enabled", () -> "false");
        registry.add("app.normalization-candidate-collection.enabled", () -> "false");
    }

    private static String backendMigrationLocation() {
        Path cwd = Path.of("").toAbsolutePath();

        List<Path> candidates = List.of(
                cwd.resolve("backend/src/main/resources/db/migration"),
                cwd.resolve("../backend/src/main/resources/db/migration"),
                cwd.getParent() == null
                        ? cwd.resolve("backend/src/main/resources/db/migration")
                        : cwd.getParent().resolve("backend/src/main/resources/db/migration")
        );

        return candidates.stream()
                .filter(Files::isDirectory)
                .findFirst()
                .map(path -> "filesystem:" + path.toAbsolutePath())
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot find backend Flyway migration directory from " + cwd
                ));
    }
}
