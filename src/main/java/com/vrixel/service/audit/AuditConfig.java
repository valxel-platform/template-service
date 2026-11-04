package com.vrixel.service.audit;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Duration;
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

    static String[] traceIds(Tracer tracer) {
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null) {
            return new String[] {"", ""};
        }
        return new String[] {span.context().traceId(), span.context().spanId()};
    }
}
