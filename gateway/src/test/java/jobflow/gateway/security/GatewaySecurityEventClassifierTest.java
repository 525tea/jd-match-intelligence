package jobflow.gateway.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySecurityEventClassifierTest {

    private final GatewaySecurityEventClassifier classifier = new GatewaySecurityEventClassifier();

    @Test
    void classifiesRateLimitBeforeOtherFailures() {
        SecurityEventType eventType = classifier.classify("/api/jobs", 429, true);

        assertThat(eventType).isEqualTo(SecurityEventType.RATE_LIMIT_HIT);
    }

    @Test
    void doesNotClassifyBackendPropagatedTooManyRequestsAsGatewayRateLimit() {
        SecurityEventType eventType = classifier.classify("/api/jobs", 429, false);

        assertThat(eventType).isEqualTo(SecurityEventType.ACCESS);
    }

    @Test
    void classifiesUnauthorizedAsAuthFailure() {
        SecurityEventType eventType = classifier.classify("/api/jobs", 401, false);

        assertThat(eventType).isEqualTo(SecurityEventType.AUTH_FAILURE);
    }

    @Test
    void classifiesMissingApiPathAsAbnormalRequest() {
        SecurityEventType eventType = classifier.classify("/api/unknown", 404, false);

        assertThat(eventType).isEqualTo(SecurityEventType.ABNORMAL_REQUEST);
    }

    @Test
    void classifiesMethodNotAllowedAsAbnormalRequest() {
        SecurityEventType eventType = classifier.classify("/api/jobs", 405, false);

        assertThat(eventType).isEqualTo(SecurityEventType.ABNORMAL_REQUEST);
    }

    @Test
    void classifiesSensitiveProbePathAsAbnormalRequest() {
        SecurityEventType eventType = classifier.classify("/api/.env", 404, false);

        assertThat(eventType).isEqualTo(SecurityEventType.ABNORMAL_REQUEST);
    }

    @Test
    void classifiesSensitiveProbePathAsAbnormalRequestBeforeAuthFailure() {
        SecurityEventType eventType = classifier.classify("/api/.env", 401, false);

        assertThat(eventType).isEqualTo(SecurityEventType.ABNORMAL_REQUEST);
    }

    @Test
    void doesNotClassifyAllowedActuatorHealthAsAbnormalRequest() {
        SecurityEventType eventType = classifier.classify("/actuator/health", 200, false);

        assertThat(eventType).isEqualTo(SecurityEventType.ACCESS);
    }

    @Test
    void classifiesServerErrorsAsBackendFailure() {
        SecurityEventType eventType = classifier.classify("/api/jobs", 503, false);

        assertThat(eventType).isEqualTo(SecurityEventType.BACKEND_FAILURE);
    }
}
