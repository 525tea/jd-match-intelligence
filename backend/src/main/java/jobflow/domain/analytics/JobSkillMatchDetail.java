package jobflow.domain.analytics;

import java.util.List;

public record JobSkillMatchDetail(
        JobSkillMatchSummary summary,
        List<String> matchedRequiredSkills,
        List<String> missingRequiredSkills,
        List<String> matchedPreferredSkills,
        List<String> missingPreferredSkills
) {
}
