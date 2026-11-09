package com.vrixel.service.audit;

/** Where audit records go. {@code key} keeps records about one resource in order. */
public interface AuditSink {

    /** Publish and wait until the record is safely stored; throw if it isn't. */
    void publish(String key, String json);

    /**
     * Publish without waiting (records nobody's request depends on: refusals, and every record of
     * a product that sets app.audit.required=false). A failure is logged, never thrown.
     */
    default void publishAsync(String key, String json) {
        try {
            publish(key, json);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("audit")
                    .error("audit record dropped ({}): {}", e.getClass().getSimpleName(), key);
        }
    }
}
