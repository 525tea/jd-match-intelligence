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

        assertThat(intent.roles()).isEmpty();
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
}
