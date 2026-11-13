package com.valxel.service.events;

import java.util.Map;

/** Where events go: Kafka in deployed environments, the log locally. */
public interface EventSink {

    /**
     * Publish one event and return where it landed. Throws if the event can't be confirmed; the
     * caller must then report the change as not made.
     */
    Position publish(String topic, String key, Map<String, String> headers, String json);

    /** Where an event landed. The log sink, which keeps nothing, answers partition and offset -1. */
    record Position(String topic, int partition, long offset) {}
}
