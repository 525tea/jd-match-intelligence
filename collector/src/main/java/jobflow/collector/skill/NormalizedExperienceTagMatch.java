package jobflow.collector.skill;

import java.math.BigDecimal;

public record NormalizedExperienceTagMatch(
        ExperienceTagCode tagCode,
        String sourcePhrase,
        BigDecimal confidence
) {
}
