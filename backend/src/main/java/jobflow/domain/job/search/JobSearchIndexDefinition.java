package jobflow.domain.job.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JobSearchIndexDefinition {

    private static final String ANALYZER_NAME = "jobflow_korean_tech";
    private static final String TECH_STACK_CHAR_FILTER_NAME = "jobflow_tech_stack_normalizer";
    private static final String SYNONYM_FILTER_NAME = "jobflow_tech_synonym";

    public Map<String, Object> settings() {
        return mapOf(
                "analysis", mapOf(
                        "char_filter", mapOf(
                                TECH_STACK_CHAR_FILTER_NAME, mapOf(
                                        "type", "mapping",
                                        "mappings", List.of(
                                                "ASP.NET => aspnet",
                                                "Objective-C => objectivec",
                                                "Node.js => nodejs",
                                                ".NET => dotnet",
                                                "C++ => cplusplus",
                                                "C# => csharp"
                                        )
                                )
                        ),
                        "filter", mapOf(
                                SYNONYM_FILTER_NAME, mapOf(
                                        "type", "synonym",
                                        "synonyms", List.of(
                                                "k8s, kubernetes",
                                                "js, javascript",
                                                "spring, spring boot",
                                                "백엔드, backend",
                                                "쿠버네티스, kubernetes",
                                                "스프링, spring"
                                        )
                                )
                        ),
                        "analyzer", mapOf(
                                ANALYZER_NAME, mapOf(
                                        "type", "custom",
                                        "tokenizer", "nori_tokenizer",
                                        "char_filter", List.of(TECH_STACK_CHAR_FILTER_NAME),
                                        "filter", List.of(
                                                "lowercase",
                                                SYNONYM_FILTER_NAME
                                        )
                                )
                        )
                )
        );
    }

    public Map<String, Object> mapping() {
        return mapOf(
                "properties", mapOf(
                        "id", keywordField(),
                        "source", keywordField(),
                        "externalId", keywordField(),
                        "canonicalFingerprint", keywordField(),
                        "title", searchTextField(),
                        "companyName", searchTextField(),
                        "description", searchTextField(),
                        "role", keywordField(),
                        "roleDetail", searchTextField(),
                        "careerLevel", keywordField(),
                        "employmentType", keywordField(),
                        "industry", searchTextField(),
                        "locationCountry", keywordField(),
                        "locationRegion", searchTextField(),
                        "locationCity", searchTextField(),
                        "remoteType", keywordField(),
                        "deadlineAt", dateField(),
                        "createdAt", dateField(),
                        "updatedAt", dateField()
                )
        );
    }

    private Map<String, Object> searchTextField() {
        return mapOf(
                "type", "text",
                "analyzer", ANALYZER_NAME,
                "search_analyzer", ANALYZER_NAME
        );
    }

    private Map<String, Object> keywordField() {
        return mapOf("type", "keyword");
    }

    private Map<String, Object> dateField() {
        return mapOf("type", "date");
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must contain key/value pairs");
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            map.put((String) keyValues[index], keyValues[index + 1]);
        }

        return map;
    }
}
