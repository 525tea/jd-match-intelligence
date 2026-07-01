package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;

import jobflow.domain.job.CareerLevel;
import jobflow.domain.job.JobRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobSearchIntentParserTest {

    private final JobSearchIntentParser parser = new JobSearchIntentParser();

    @Test
    @DisplayName("검색어에서 role, career, location 의도를 추출한다")
    void parseStructuredSearchIntent() {
        JobSearchIntent intent = parser.parse("백엔드 주니어 서울");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.careerLevels()).containsExactly(CareerLevel.JUNIOR);
        assertThat(intent.locationRegions()).containsExactly("Seoul");
        assertThat(intent.requiredSkillKeywords()).isEmpty();
        assertThat(intent.hasAnySignal()).isTrue();
    }

    @Test
    @DisplayName("영문 검색어에서도 role, career, location 의도를 추출한다")
    void parseEnglishStructuredSearchIntent() {
        JobSearchIntent intent = parser.parse("backend junior seoul");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.careerLevels()).containsExactly(CareerLevel.JUNIOR);
        assertThat(intent.locationRegions()).containsExactly("Seoul");
        assertThat(intent.requiredSkillKeywords()).isEmpty();
    }

    @Test
    @DisplayName("구조화 의도가 없는 검색어는 빈 intent로 둔다")
    void parseKeywordWithoutStructuredIntent() {
        JobSearchIntent intent = parser.parse("QueryDSL");

        assertThat(intent.roles()).isEmpty();
        assertThat(intent.careerLevels()).isEmpty();
        assertThat(intent.locationRegions()).isEmpty();
        assertThat(intent.requiredSkillKeywords()).isEmpty();
        assertThat(intent.hasAnySignal()).isFalse();
    }

    @Test
    @DisplayName("명시 기술 토큰은 선택적 must 검색어로 추출한다")
    void parseRequiredSkillKeyword() {
        JobSearchIntent intent = parser.parse("C++ 개발자");

        assertThat(intent.roles()).containsExactlyInAnyOrder(
                JobRole.SOFTWARE_ENGINEER,
                JobRole.EMBEDDED_SOFTWARE,
                JobRole.ROBOT_SOFTWARE,
                JobRole.HARDWARE_ENGINEER,
                JobRole.GAME_CLIENT
        );
        assertThat(intent.careerLevels()).isEmpty();
        assertThat(intent.locationRegions()).isEmpty();
        assertThat(intent.requiredSkillKeywords()).containsExactly("C++");
        assertThat(intent.hasAnySignal()).isTrue();
    }

    @Test
    @DisplayName("정규화되는 기술 토큰도 선택적 must 검색어로 추출한다")
    void parseNormalizedRequiredSkillKeyword() {
        JobSearchIntent intent = parser.parse("Node.js 백엔드");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.requiredSkillKeywords()).containsExactly("Node.js");
    }

    @Test
    @DisplayName("Kubernetes 플랫폼 검색어는 인프라 계열 role 의도로 확장한다")
    void parseKubernetesPlatformRoles() {
        JobSearchIntent intent = parser.parse("쿠버네티스 플랫폼");

        assertThat(intent.roles()).containsExactlyInAnyOrder(
                JobRole.DEVOPS,
                JobRole.SRE,
                JobRole.SYSTEM_NETWORK
        );
        assertThat(intent.requiredSkillKeywords()).containsExactly("Kubernetes");
    }

    @Test
    @DisplayName("Spring Boot JPA 검색어는 JPA를 hard filter로 쓰지 않고 백엔드 의도와 Spring Boot 기술로 해석한다")
    void parseSpringBootJpaAsBackendIntentWithoutJpaHardFilter() {
        JobSearchIntent intent = parser.parse("Spring Boot JPA");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.requiredSkillKeywords()).containsExactly("Spring Boot");
    }

    @Test
    @DisplayName("Python Django 검색어는 백엔드 의도와 Python/Django 기술로 해석한다")
    void parsePythonDjangoAsBackendFrameworkIntent() {
        JobSearchIntent intent = parser.parse("Python Django");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.requiredSkillKeywords()).containsExactlyInAnyOrder("Python", "Django");
    }

    @Test
    @DisplayName("Go Fiber 검색어는 짧은 Go token을 오탐하지 않고 백엔드 framework 조합으로 해석한다")
    void parseGoFiberAsBackendFrameworkIntent() {
        JobSearchIntent intent = parser.parse("Go Fiber");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.requiredSkillKeywords()).containsExactlyInAnyOrder("Go", "Fiber");
    }

    @Test
    @DisplayName("py 데이터 검색어는 Python 기술과 데이터 계열 role 의도로 해석한다")
    void parsePyDataAsPythonDataIntent() {
        JobSearchIntent intent = parser.parse("py 데이터");

        assertThat(intent.roles()).containsExactly(JobRole.DATA_ENGINEER);
        assertThat(intent.requiredSkillKeywords()).containsExactly("Python");
    }

    @Test
    @DisplayName("Django 검색어는 Go token으로 부분 매칭하지 않는다")
    void parseDoesNotMatchGoInsideDjango() {
        JobSearchIntent intent = parser.parse("Django");

        assertThat(intent.roles()).containsExactly(JobRole.BACKEND);
        assertThat(intent.requiredSkillKeywords()).containsExactly("Django");
    }

    @Test
    @DisplayName("MLOps 검색어는 MLOps role과 명시 skill로 함께 해석한다")
    void parseMlopsAsRoleAndRequiredSkill() {
        JobSearchIntent intent = parser.parse("mlops 엔지니어");

        assertThat(intent.roles()).containsExactlyInAnyOrder(JobRole.ML_ENGINEER, JobRole.MLOPS);
        assertThat(intent.requiredSkillKeywords()).containsExactly("MLOps");
    }

    @Test
    @DisplayName("AI 엔지니어 검색어는 AI 계열 role 의도로 확장한다")
    void parseAiEngineerRoles() {
        JobSearchIntent intent = parser.parse("AI 엔지니어");

        assertThat(intent.roles()).containsExactlyInAnyOrder(
                JobRole.ML_ENGINEER,
                JobRole.AI_ENGINEER
        );
    }

    @Test
    @DisplayName("AI 단어는 다른 영문 token 내부에서 부분 매칭하지 않는다")
    void parseDoesNotMatchAiInsideAnotherToken() {
        JobSearchIntent intent = parser.parse("Airflow 데이터 엔지니어");

        assertThat(intent.roles()).contains(
                JobRole.DATA_ENGINEER
        );
        assertThat(intent.roles()).doesNotContain(
                JobRole.AI_ENGINEER,
                JobRole.ML_ENGINEER
        );
    }
}
