package jobflow.domain.matching;

import java.math.BigDecimal;

public final class JdMatchScoreWeights {

    public static final BigDecimal REQUIRED_SKILL = new BigDecimal("0.45");
    public static final BigDecimal PREFERRED_SKILL = new BigDecimal("0.20");
    public static final BigDecimal EXPERIENCE_TAG = new BigDecimal("0.20");
    public static final BigDecimal CAREER_LEVEL = new BigDecimal("0.10");
    public static final BigDecimal CONFIDENCE = new BigDecimal("0.05");

    public static final BigDecimal TOTAL = REQUIRED_SKILL
            .add(PREFERRED_SKILL)
            .add(EXPERIENCE_TAG)
            .add(CAREER_LEVEL)
            .add(CONFIDENCE);

    private JdMatchScoreWeights() {
    }
}
