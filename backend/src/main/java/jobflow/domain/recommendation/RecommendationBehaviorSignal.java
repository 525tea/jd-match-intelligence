package jobflow.domain.recommendation;

public enum RecommendationBehaviorSignal {
    NONE(1.00),
    VIEWED(0.70),
    SAVED(0.40);

    private final double rate;

    RecommendationBehaviorSignal(double rate) {
        this.rate = rate;
    }

    public double rate() {
        return rate;
    }
}
