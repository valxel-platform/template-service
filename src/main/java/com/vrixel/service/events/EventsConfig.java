package com.vrixel.service.events;

import com.vrixel.service.audit.AuditConfig;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EventsProperties.class)
public class EventsConfig {

    @Bean
    EventSink eventSink(
            EventsProperties props,
            @Value("${spring.application.name}") String service,
            @Value("${app.audit.data-class:general}") String dataClass) {
        return sink(props, service, dataClass);
    }

    static EventSink sink(EventsProperties props, String service) {
        return sink(props, service, "general");
    }

    static EventSink sink(EventsProperties props, String service, String dataClass) {
        return switch (props.sink()) {
            case "kafka" -> {
                if (props.bootstrap().isEmpty()) {
                    throw new IllegalStateException(
                            "Set app.events.bootstrap (EVENTS_KAFKA_BOOTSTRAP) for the kafka event sink");
                }
                AuditConfig.requireEncryption(props.kafka(), dataClass, "events");
                yield KafkaEventSink.create(
                        props.bootstrap(), service + "-events", props.kafka(), Duration.ofMillis(props.timeoutMs()));
            }
            case "log" -> new LogEventSink();
            default -> throw new IllegalStateException("Unknown event sink " + props.sink() + ": use kafka or log");
        };
    }

    @Bean
    Events events(
            EventSink sink,
            EventsProperties props,
            @Value("${spring.application.name}") String service,
            ObjectProvider<Tracer> tracer) {
        return new Events(sink, props, service, Clock.systemUTC(), () -> AuditConfig.traceContext(tracer.getIfAvailable()));
    }
}
