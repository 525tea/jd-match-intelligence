package jobflow.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
public abstract class MySqlTestContainerSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("jobflow_test")
            .withUsername("jobflow")
            .withPassword("jobflow");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);

        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.batch.jdbc.initialize-schema", () -> "never");
        registry.add("spring.batch.job.enabled", () -> "false");

        registry.add("app.search.elasticsearch.initialize-on-startup", () -> "false");
        registry.add("app.search.elasticsearch.reindex-on-startup", () -> "false");
        registry.add("jobflow.outbox.relay.enabled", () -> "false");
        registry.add("jobflow.notification.deadline-reminder.scheduler.enabled", () -> "false");
        registry.add("jobflow.notification.deadline-reminder.runner.enabled", () -> "false");
        registry.add("jobflow.notification.daily-digest.runner.enabled", () -> "false");
        registry.add("jobflow.analytics.skill-trend.scheduler.enabled", () -> "false");
        registry.add("jobflow.analytics.skill-trend.runner.enabled", () -> "false");
    }
}
