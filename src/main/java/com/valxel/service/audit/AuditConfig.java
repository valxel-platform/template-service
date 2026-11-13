package com.valxel.service.audit;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditProperties.class)
public class AuditConfig {

    @Bean
    AuditSink auditSink(AuditProperties props) {
        return sink(props);
    }

    static AuditSink sink(AuditProperties props) {
        return switch (props.sink()) {
            case "kafka" -> {
                if (props.bootstrap().isEmpty()) {
                    throw new IllegalStateException(
                            "Set app.audit.bootstrap (AUDIT_KAFKA_BOOTSTRAP) for the kafka audit sink");
                }
                requireEncryption(props.kafka(), props.dataClass(), "audit");
                yield KafkaAuditSink.create(
                        props.bootstrap(), props.topic(), props.kafka(), Duration.ofMillis(props.timeoutMs()));
            }
            case "log" -> new LogAuditSink();
            default -> throw new IllegalStateException("Unknown audit sink " + props.sink() + ": use kafka or log");
        };
    }

    @Bean
    Auditor auditor(
            AuditSink sink,
            AuditProperties props,
            @Value("${spring.application.name}") String service,
            ObjectProvider<Tracer> tracer) {
        return new Auditor(sink, props, service, Clock.systemUTC(), () -> traceIds(tracer.getIfAvailable()));
    }

    /** Data classes whose Kafka traffic must be encrypted: it names people and their records. */
    public static final Set<String> ENCRYPTED_CLASSES = Set.of("phi", "payments");

    /**
     * PHI and payments products never talk to Kafka in plain text: refuse to start instead.
     *
     * @param kafka the client settings (security.protocol and friends)
     */
    public static void requireEncryption(Map<String, String> kafka, String dataClass, String what) {
        String protocol = kafka.getOrDefault("security.protocol", "PLAINTEXT").toUpperCase(Locale.ROOT);
        if (ENCRYPTED_CLASSES.contains(dataClass) && !Set.of("SSL", "SASL_SSL").contains(protocol)) {
            throw new IllegalStateException("The " + what + " Kafka connection of a " + dataClass
                    + " product must use TLS: set security.protocol to SSL or SASL_SSL (it is " + protocol + ")");
        }
    }

    /**
     * The current trace and span ids and whether the trace is sampled ("1" or "0"), or empty
     * strings outside a trace.
     */
    public static String[] traceContext(Tracer tracer) {
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null) {
            return new String[] {"", "", ""};
        }
        Boolean sampled = span.context().sampled();
        return new String[] {span.context().traceId(), span.context().spanId(), Boolean.FALSE.equals(sampled) ? "0" : "1"};
    }

    /** The current trace and span ids, or empty strings outside a trace. */
    public static String[] traceIds(Tracer tracer) {
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null) {
            return new String[] {"", ""};
        }
        return new String[] {span.context().traceId(), span.context().spanId()};
    }
}
