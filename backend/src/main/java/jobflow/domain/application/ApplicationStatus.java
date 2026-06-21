package jobflow.domain.application;

import java.util.Map;
import java.util.Set;

public enum ApplicationStatus {
    APPLIED,
    DOCUMENT_PASSED,
    CODING_TEST,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> TRANSITIONS = Map.of(
            APPLIED, Set.of(DOCUMENT_PASSED, CODING_TEST, INTERVIEW, REJECTED, WITHDRAWN),
            DOCUMENT_PASSED, Set.of(CODING_TEST, INTERVIEW, OFFER, REJECTED, WITHDRAWN),
            CODING_TEST, Set.of(INTERVIEW, OFFER, REJECTED, WITHDRAWN),
            INTERVIEW, Set.of(OFFER, REJECTED, WITHDRAWN),
            OFFER, Set.of(WITHDRAWN),
            REJECTED, Set.of(),
            WITHDRAWN, Set.of()
    );

    public boolean canTransitionTo(ApplicationStatus nextStatus) {
        return this == nextStatus || TRANSITIONS.getOrDefault(this, Set.of()).contains(nextStatus);
    }
}
