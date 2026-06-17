package jobflow.global.config;

import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.ThreadLocalCurrentTraceContext;
import brave.sampler.Sampler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.brave.bridge.BraveCurrentTraceContext;
import io.micrometer.tracing.brave.bridge.BravePropagator;
import io.micrometer.tracing.brave.bridge.BraveTracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.reporter.Sender;
import zipkin2.reporter.brave.AsyncZipkinSpanHandler;
import zipkin2.reporter.urlconnection.URLConnectionSender;

@Configuration
@ConditionalOnClass({Tracing.class, AsyncZipkinSpanHandler.class, URLConnectionSender.class})
public class ZipkinTracingConfig {

    @Bean
    public Sender zipkinSender(
            @Value("${management.zipkin.tracing.endpoint:http://localhost:9411/api/v2/spans}") String endpoint
    ) {
        return URLConnectionSender.newBuilder()
                .endpoint(endpoint)
                .build();
    }

    @Bean(destroyMethod = "close")
    public AsyncZipkinSpanHandler zipkinSpanHandler(Sender zipkinSender) {
        return AsyncZipkinSpanHandler.create(zipkinSender);
    }

    @Bean
    public CurrentTraceContext braveCurrentTraceContext() {
        return ThreadLocalCurrentTraceContext.create();
    }

    @Bean(destroyMethod = "close")
    public Tracing braveTracing(
            AsyncZipkinSpanHandler zipkinSpanHandler,
            CurrentTraceContext braveCurrentTraceContext,
            @Value("${spring.application.name:jobflow}") String applicationName,
            @Value("${management.tracing.sampling.probability:1.0}") double samplingProbability
    ) {
        return Tracing.newBuilder()
                .localServiceName(applicationName)
                .currentTraceContext(braveCurrentTraceContext)
                .sampler(Sampler.create((float) samplingProbability))
                .addSpanHandler(zipkinSpanHandler)
                .build();
    }

    @Bean
    public BraveCurrentTraceContext micrometerCurrentTraceContext(Tracing braveTracing) {
        return new BraveCurrentTraceContext(braveTracing.currentTraceContext());
    }

    @Bean
    public Tracer micrometerTracer(
            Tracing braveTracing,
            BraveCurrentTraceContext micrometerCurrentTraceContext
    ) {
        return new BraveTracer(braveTracing.tracer(), micrometerCurrentTraceContext);
    }

    @Bean
    public Propagator micrometerPropagator(Tracing braveTracing) {
        return new BravePropagator(braveTracing);
    }

    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> tracingObservationRegistryCustomizer(
            Tracer micrometerTracer,
            Propagator micrometerPropagator
    ) {
        return registry -> registry.observationConfig()
                .observationHandler(new PropagatingReceiverTracingObservationHandler<>(micrometerTracer, micrometerPropagator))
                .observationHandler(new PropagatingSenderTracingObservationHandler<>(micrometerTracer, micrometerPropagator))
                .observationHandler(new DefaultTracingObservationHandler(micrometerTracer));
    }
}
