package com.vrixel.service.events;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Domain event settings (app.events.*).
 *
 * @param sink {@code kafka} in deployed environments, {@code log} locally
 * @param bootstrap the product's Kafka
 * @param topicPrefix topics are {@code <prefix>.<entity>.v1}; usually {@code <product>.<service>}
 * @param source the CloudEvents source, usually {@code <product>/<service>}
 * @param timeoutMs how long to wait for Kafka's acknowledgement before answering 503
 * @param kafka extra Kafka client settings (security.protocol, sasl.*)
 */
@ConfigurationProperties("app.events")
public record EventsProperties(
        String sink, String bootstrap, String topicPrefix, String source, Integer timeoutMs, Map<String, String> kafka) {

    public EventsProperties {
        sink = blankTo(sink, "log");
        bootstrap = blankTo(bootstrap, "");
        topicPrefix = blankTo(topicPrefix, "local");
        source = blankTo(source, "");
        timeoutMs = timeoutMs == null ? 5000 : timeoutMs;
        kafka = kafka == null ? Map.of() : Map.copyOf(kafka);
    }

    /** The topic for one entity's events, e.g. emr.intake-api.patients.v1. */
    public String topic(String entity) {
        return topicPrefix + "." + entity + ".v1";
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
