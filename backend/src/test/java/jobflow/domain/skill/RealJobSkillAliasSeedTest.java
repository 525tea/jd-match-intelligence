package jobflow.domain.skill;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RealJobSkillAliasSeedTest {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM skill_aliases");
        jdbcTemplate.execute("DELETE FROM skills");

        skillRepository.save(Skill.create("Node.js", "node.js", SkillCategory.FRAMEWORK));
        skillRepository.save(Skill.create("PostgreSQL", "postgresql", SkillCategory.DATABASE));
        skillRepository.save(Skill.create("Oracle Database", "oracle database", SkillCategory.DATABASE));
        skillRepository.save(Skill.create("Kubernetes", "kubernetes", SkillCategory.INFRA));
        skillRepository.flush();

        jdbcTemplate.execute("ALTER TABLE skills ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");
        jdbcTemplate.execute("ALTER TABLE skill_aliases ALTER COLUMN enabled SET DEFAULT TRUE");
        jdbcTemplate.execute("ALTER TABLE skill_aliases ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V9__add_real_job_skill_aliases.sql")
        );
        populator.execute(dataSource);

        ResourceDatabasePopulator securityNetworkPopulator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V10__add_security_network_skill_aliases.sql")
        );
        securityNetworkPopulator.execute(dataSource);
    }

    @Test
    @DisplayName("실제 공고 long-tail 언어 seed가 생성된다")
    void seedRealJobLongTailLanguages() {
        assertSkillSeed("c++", "C++", SkillCategory.LANGUAGE);
        assertSkillSeed("go", "Go", SkillCategory.LANGUAGE);
        assertSkillSeed("abap", "ABAP", SkillCategory.LANGUAGE);
        assertSkillSeed("verilog", "Verilog", SkillCategory.LANGUAGE);
    }

    @Test
    @DisplayName("embedded/robotics 계열 seed가 생성된다")
    void seedEmbeddedAndRoboticsSkills() {
        assertSkillSeed("rtos", "RTOS", SkillCategory.INFRA);
        assertSkillSeed("embedded linux", "Embedded Linux", SkillCategory.INFRA);
        assertSkillSeed("ros", "ROS", SkillCategory.FRAMEWORK);
        assertSkillSeed("ros2", "ROS2", SkillCategory.FRAMEWORK);
        assertSkillSeed("fpga", "FPGA", SkillCategory.ETC);
    }

    @Test
    @DisplayName("실제 공고 alias가 기준 skill로 연결된다")
    void seedRealJobSkillAliases() {
        assertAlias("golang", "go");
        assertAlias("node", "node.js");
        assertAlias("postgres", "postgresql");
        assertAlias("firmware", "rtos");
        assertAlias("펌웨어", "rtos");
        assertAlias("sap", "sap erp");
        assertAlias("erp", "sap erp");
        assertAlias("s w", "software engineering");
        assertAlias("소프트웨어 개발", "software engineering");
    }

    @Test
    @DisplayName("security/network/hardware 계열 seed와 alias가 생성된다")
    void seedSecurityNetworkHardwareSkills() {
        assertSkillSeed("network", "Network", SkillCategory.INFRA);
        assertSkillSeed("tcp/ip", "TCP/IP", SkillCategory.INFRA);
        assertSkillSeed("bgp", "BGP", SkillCategory.INFRA);
        assertSkillSeed("isms", "ISMS", SkillCategory.METHODOLOGY);
        assertSkillSeed("cissp", "CISSP", SkillCategory.METHODOLOGY);
        assertSkillSeed("rf", "RF", SkillCategory.ETC);
        assertSkillSeed("spectrum analyzer", "Spectrum Analyzer", SkillCategory.TOOL);

        assertAlias("네트워크", "network");
        assertAlias("tcp ip", "tcp/ip");
        assertAlias("apache", "apache http server");
        assertAlias("정보보호 인증", "isms");
    }

    private void assertSkillSeed(String normalizedName, String name, SkillCategory category) {
        Skill skill = skillRepository.findAllByOrderByNameAsc()
                .stream()
                .filter(candidate -> candidate.getNormalizedName().equals(normalizedName))
                .findFirst()
                .orElseThrow();

        assertThat(skill.getName()).isEqualTo(name);
        assertThat(skill.getCategory()).isEqualTo(category);
    }

    private void assertAlias(String normalizedAlias, String normalizedSkillName) {
        String actualSkillName = jdbcTemplate.queryForObject(
                """
                        SELECT s.normalized_name
                        FROM skill_aliases sa
                                 JOIN skills s ON s.id = sa.skill_id
                        WHERE sa.normalized_alias = ?
                          AND sa.enabled = TRUE
                        """,
                String.class,
                normalizedAlias
        );

        assertThat(actualSkillName).isEqualTo(normalizedSkillName);
    }
}
