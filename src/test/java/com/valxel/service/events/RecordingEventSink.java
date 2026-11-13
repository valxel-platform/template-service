package com.valxel.service.events;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** An in-memory event sink for tests; {@code fail} makes it refuse events, as Kafka down would. */
public class RecordingEventSink implements EventSink {

    public record Sent(String topic, String key, Map<String, String> headers, DocumentContext event) {}

    public final List<Sent> sent = new ArrayList<>();
    public boolean fail;

    @Override
    public Position publish(String topic, String key, Map<String, String> headers, String json) {
        if (fail) {
            throw new EventsUnavailableException("kafka down", null);
        }
        sent.add(new Sent(topic, key, Map.copyOf(headers), JsonPath.parse(json)));
        return new Position(topic, 0, sent.size() - 1L);
    }

    public static Events events(RecordingEventSink sink) {
        EventsProperties props = new EventsProperties(null, null, "emr.intake-api", "emr/intake-api", null, null);
        return new Events(sink, props, "template-service", Clock.systemUTC(), () -> new String[] {"t".repeat(32), "s".repeat(16)});
    }
}
