package com.vrixel.service.audit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The audit trail's settings (app.audit.*).
 *
 * @param sink {@code kafka} in deployed environments, {@code log} locally
 * @param bootstrap the product's Kafka
 * @param topic the product's audit topic
 * @param required whether an action whose record can't be published fails (the default)
 * @param timeoutMs how long to wait for Kafka's acknowledgement
 * @param product the product id, e.g. emr
 * @param environment e.g. production
 * @param dataClass general, phi or payments
 * @param kafka extra Kafka client settings (security.protocol, sasl.*)
 */
@ConfigurationProperties("app.audit")
public record AuditProperties(
        String sink,
        String bootstrap,
        String topic,
        Boolean required,
        Integer timeoutMs,
        String product,
        String environment,
        String dataClass,
        Map<String, String> kafka) {

    public AuditProperties {
        sink = blankTo(sink, "log");
        bootstrap = blankTo(bootstrap, "");
        topic = blankTo(topic, "audit.v1");
        required = required == null || required;
        timeoutMs = timeoutMs == null ? 5000 : timeoutMs;
        product = blankTo(product, "");
        environment = blankTo(environment, "");
        dataClass = blankTo(dataClass, "general");
        kafka = kafka == null ? Map.of() : Map.copyOf(kafka);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    /** Whether to fail actions whose records can't be published. */
    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }
}
