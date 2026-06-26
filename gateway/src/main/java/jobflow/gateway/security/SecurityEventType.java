package jobflow.gateway.security;

public enum SecurityEventType {
    ACCESS,
    AUTH_FAILURE,
    RATE_LIMIT_HIT,
    ABNORMAL_REQUEST,
    BACKEND_FAILURE
}
