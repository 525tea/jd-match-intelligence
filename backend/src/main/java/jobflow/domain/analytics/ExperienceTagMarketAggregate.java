package jobflow.domain.analytics;

import jobflow.domain.skill.ExperienceTagCode;

public record ExperienceTagMarketAggregate(
        ExperienceTagCode tagCode,
        Long jobCount
) {
}
