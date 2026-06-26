package jobflow.gateway.security;

public interface GatewaySecurityEventPublisher {

    void publish(GatewaySecurityEvent event);
}
