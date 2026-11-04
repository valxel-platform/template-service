package com.vrixel.service.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One JSON line per record on the {@code audit} logger, for local work. */
public class LogAuditSink implements AuditSink {

    private static final Logger log = LoggerFactory.getLogger("audit");

    @Override
    public void publish(String key, String json) {
        log.info(json);
    }
}
