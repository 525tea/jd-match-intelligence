package jobflow.domain.job.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JobSearchIndexDefinitionTest {

    private final JobSearchIndexDefinition definition = new JobSearchIndexDefinition();

    @Test
    @DisplayName("nori tokenizer와 기술 용어 char filter/synonym filter를 포함한 analyzer 설정을 만든다")
    void settings() {
        Map<String, Object> settings = definition.settings();

        assertThat(settings)
                .containsEntry("number_of_shards", 1)
                .containsEntry("number_of_replicas", 0);

        Map<String, Object> analysis = getMap(settings, "analysis");
        Map<String, Object> analyzers = getMap(analysis, "analyzer");
        Map<String, Object> charFilters = getMap(analysis, "char_filter");
        Map<String, Object> filters = getMap(analysis, "filter");

        Map<String, Object> analyzer = getMap(analyzers, "jobflow_korean_tech");
        Map<String, Object> techStackCharFilter = getMap(charFilters, "jobflow_tech_stack_normalizer");
        Map<String, Object> synonymFilter = getMap(filters, "jobflow_tech_synonym");

        assertThat(analyzer)
                .containsEntry("type", "custom")
                .containsEntry("tokenizer", "nori_tokenizer")
                .containsEntry("char_filter", List.of("jobflow_tech_stack_normalizer"))
                .containsEntry("filter", List.of("lowercase", "jobflow_tech_synonym"));

        assertThat(techStackCharFilter)
                .containsEntry("type", "mapping");

        assertThat((List<String>) techStackCharFilter.get("mappings"))
                .contains(
                        "ASP.NET => aspnet",
                        "Objective-C => objectivec",
                        "Node.js => nodejs",
                        ".NET => dotnet",
                        "C++ => cplusplus",
                        "C# => csharp"
                );

        assertThat(synonymFilter)
                .containsEntry("type", "synonym");

        assertThat((List<String>) synonymFilter.get("synonyms"))
                .contains(
                        "k8s, kubernetes",
                        "js, javascript",
                        "spring, spring boot",
                        "백엔드, backend",
                        "쿠버네티스, kubernetes",
                        "스프링, spring"
                );
    }

    @Test
    @DisplayName("공고 검색 field에 analyzer와 keyword/date mapping을 적용한다")
    void mapping() {
        Map<String, Object> mapping = definition.mapping();

        Map<String, Object> properties = getMap(mapping, "properties");

        assertTextField(properties, "title");
        assertTextField(properties, "companyName");
        assertTextField(properties, "description");
        assertTextField(properties, "roleDetail");
        assertTextField(properties, "industry");
        assertTextField(properties, "locationRegion");
        assertTextField(properties, "locationCity");

        assertKeywordField(properties, "id");
        assertKeywordField(properties, "source");
        assertKeywordField(properties, "externalId");
        assertKeywordField(properties, "role");
        assertKeywordField(properties, "careerLevel");
        assertKeywordField(properties, "employmentType");
        assertKeywordField(properties, "remoteType");

        assertThat(getMap(properties, "deadlineAt")).containsEntry("type", "date");
        assertThat(getMap(properties, "createdAt")).containsEntry("type", "date");
        assertThat(getMap(properties, "updatedAt")).containsEntry("type", "date");
    }

    private void assertTextField(Map<String, Object> properties, String fieldName) {
        assertThat(getMap(properties, fieldName))
                .containsEntry("type", "text")
                .containsEntry("analyzer", "jobflow_korean_tech")
                .containsEntry("search_analyzer", "jobflow_korean_tech");
    }

    private void assertKeywordField(Map<String, Object> properties, String fieldName) {
        assertThat(getMap(properties, fieldName))
                .containsEntry("type", "keyword");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        return (Map<String, Object>) source.get(key);
    }
}
