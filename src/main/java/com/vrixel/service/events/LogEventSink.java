package com.vrixel.service.events;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * For local runs without Kafka: logs each event's type and subject (never its data) and keeps
 * nothing. Deployed environments use the kafka sink.
 */
public class LogEventSink implements EventSink {

    private static final Logger log = LoggerFactory.getLogger("events");

    @Override
    public Position publish(String topic, String key, Map<String, String> headers, String json) {
        log.info("event {} {} on {} (log sink: not delivered)", headers.get("ce_type"), headers.get("ce_subject"), topic);
        return new Position(topic, -1, -1);
    }
}
