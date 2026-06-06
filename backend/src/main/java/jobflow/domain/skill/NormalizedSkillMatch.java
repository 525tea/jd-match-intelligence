package jobflow.domain.skill;

import java.math.BigDecimal;

public record NormalizedSkillMatch(
        Skill skill,
        String sourceAlias,
        String normalizedAlias,
        BigDecimal confidence
) {
}
