package com.vrixel.service.events;

import com.vrixel.service.json.Json;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Publishes domain events (see the package documentation). */
public class Events {

    /** Event types are {@code <prefix>.<entity>.<what happened>.v1}, e.g. vrixel.patient.registered.v1. */
    public static final String TYPE_PREFIX = "vrixel.";

    /** The current trace and span ids, or empty strings outside a trace. */
    public interface TraceIds extends Supplier<String[]> {}

    /** The published event's id and where it landed. */
    public record Published(String eventId, String type, EventSink.Position position) {}

    private final EventSink sink;
    private final EventsProperties props;
    private final String service;
    private final Clock clock;
    private final TraceIds traceIds;

    public Events(EventSink sink, EventsProperties props, String service, Clock clock, TraceIds traceIds) {
        this.sink = sink;
        this.props = props;
        this.service = service;
        this.clock = clock;
        this.traceIds = traceIds;
    }

    /**
     * Publish that {@code what} happened to record {@code id} of {@code entity}. {@code data} is the
     * new state or the change, as the event's consumers need it. With an idempotency key (the
     * caller's Idempotency-Key header) a retried request gets the same event id, so workers apply
     * it once.
     *
     * @throws EventsUnavailableException if Kafka doesn't confirm the event: the change did not happen
     */
    public Published publish(String entity, String what, String id, Map<String, ?> data, String idempotencyKey) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("an event needs the id of the record it changes");
        }
        String singular = entity.endsWith("s") ? entity.substring(0, entity.length() - 1) : entity;
        String type = TYPE_PREFIX + singular + "." + what + ".v1";
        String subject = singular + "/" + id;
        String eventId = idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : UUID.nameUUIDFromBytes((type + "\n" + subject + "\n" + idempotencyKey).getBytes(StandardCharsets.UTF_8))
                        .toString();
        String source = props.source().isBlank() ? service : props.source();

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("specversion", "1.0");
        event.put("id", eventId);
        event.put("source", source);
        event.put("type", type);
        event.put("time", Instant.now(clock).toString());
        event.put("subject", subject);
        event.put("datacontenttype", "application/json");
        event.put("data", data == null ? Map.of() : data);

        // CloudEvents' Kafka binding: the ce_ headers let workers route and filter without parsing.
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("ce_id", eventId);
        headers.put("ce_type", type);
        headers.put("ce_source", source);
        headers.put("ce_subject", subject);
        String[] trace = traceIds.get();
        if (!trace[0].isBlank() && !trace[1].isBlank()) {
            headers.put("traceparent", "00-" + trace[0] + "-" + trace[1] + "-01");
        }
        EventSink.Position at;
        try {
            at = sink.publish(props.topic(entity), id, headers, Json.write(event));
        } catch (EventsUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EventsUnavailableException("event could not be published", e);
        }
        return new Published(eventId, type, at);
    }
}
