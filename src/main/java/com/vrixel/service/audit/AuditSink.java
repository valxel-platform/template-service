package com.vrixel.service.audit;

/** Where audit records go. {@code key} keeps records about one resource in order. */
public interface AuditSink {

    void publish(String key, String json);
}
