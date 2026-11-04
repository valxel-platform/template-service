package com.vrixel.service.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds and publishes audit records (see the package documentation). */
public class Auditor {

    public static final String EVENT_TYPE = "vrixel.audit.v1";
    private static final Logger log = LoggerFactory.getLogger("audit");

    /** The current trace and span ids, or empty strings outside a trace. */
    public interface TraceIds extends Supplier<String[]> {}

    private final AuditSink sink;
    private final AuditProperties props;
    private final String service;
    private final Clock clock;
    private final TraceIds traceIds;

    public Auditor(AuditSink sink, AuditProperties props, String service, Clock clock, TraceIds traceIds) {
        this.sink = sink;
        this.props = props;
        this.service = service;
        this.clock = clock;
        this.traceIds = traceIds;
    }

    public Map<String, Object> record(String action, Actor actor, String resourceType, Collection<String> ids) {
        return record(action, actor, resourceType, ids, "success", "", "", Map.of());
    }

    /**
     * Publish one record. {@code outcome} is success, denied or failure; {@code details} may hold
     * counts, codes and ids, never field values.
     */
    public Map<String, Object> record(
            String action,
            Actor actor,
            String resourceType,
            Collection<String> ids,
            String outcome,
            String reason,
            String onBehalfOf,
            Map<String, ?> details) {
        List<String> sorted = List.copyOf(new TreeSet<>(
                ids.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).toList()));
        String[] trace = traceIds.get();
        String subject = resourceType.isBlank() ? action : resourceType + "/" + String.join(",", sorted);

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", resourceType);
        resource.put("ids", sorted);
        Map<String, Object> who = new LinkedHashMap<>();
        who.put("id", actor.id());
        who.put("type", actor.type());
        who.put("client", actor.client());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("product", props.product());
        data.put("service", service);
        data.put("environment", props.environment());
        data.put("data_class", props.dataClass());
        data.put("action", action);
        data.put("actor", who);
        data.put("on_behalf_of", onBehalfOf == null ? "" : onBehalfOf);
        data.put("resource", resource);
        data.put("outcome", outcome);
        data.put("reason", reason == null ? "" : reason.substring(0, Math.min(reason.length(), 300)));
        data.put("trace_id", trace[0]);
        data.put("span_id", trace[1]);
        data.put("details", details == null ? Map.of() : details);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("specversion", "1.0");
        event.put("id", UUID.randomUUID().toString());
        event.put("source", props.product().isBlank() ? service : props.product() + "/" + service);
        event.put("type", EVENT_TYPE);
        event.put("time", Instant.now(clock).toString());
        event.put("subject", subject);
        event.put("datacontenttype", "application/json");
        event.put("data", data);

        try {
            sink.publish(subject, Json.write(event));
        } catch (RuntimeException e) {
            if (props.isRequired()) {
                throw e instanceof AuditUnavailableException a
                        ? a
                        : new AuditUnavailableException("audit record could not be published", e);
            }
            log.error("audit record dropped ({}): {} {}", e.getClass().getSimpleName(), action, subject);
        }
        return event;
    }
}
