package com.vrixel.service.greeting;

import com.vrixel.service.audit.Actor;
import com.vrixel.service.audit.Auditor;
import com.vrixel.service.events.Events;
import com.vrixel.service.security.AccessPolicy;
import com.vrixel.service.security.Action;
import com.vrixel.service.security.Callers;
import com.vrixel.service.security.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned API (/api/v1). Change it by expanding (add fields or endpoints), migrating consumers,
 * then contracting in a later version; the contract tests catch accidental breaks.
 * The OpenAPI description is api/openapi.yaml.
 */
@RestController
@RequestMapping(path = "/api/v1/greetings", produces = MediaType.APPLICATION_JSON_VALUE)
public class GreetingController {

    private final GreetingService service;
    private final Auditor auditor;
    private final Events events;
    private final Callers callers;
    private final AccessPolicy access;

    public GreetingController(
            GreetingService service, Auditor auditor, Events events, Callers callers, AccessPolicy access) {
        this.service = service;
        this.auditor = auditor;
        this.events = events;
        this.callers = callers;
        this.access = access;
    }

    /**
     * Audited like any read of product data: who asked, never what they asked about (the name could
     * be PHI). Replace with your resource type and record ids.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_greetings:read')")
    public Greeting greet(@RequestParam @NotBlank @Size(max = 100) String name, Principal caller) {
        Greeting greeting = service.greet(name);
        auditor.record("greeting.read", callers.actor(caller), "greeting", List.of());
        return greeting;
    }

    /**
     * A command: changes state by publishing one event to Kafka, never by writing a database
     * (constitution VX-16). The answer is 202 once Kafka holds the event; the event workers apply it
     * to the read models. Kafka unavailable means 503 and no change; retry with the same
     * Idempotency-Key and the workers apply it once.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('SCOPE_greetings:write')")
    public ResponseEntity<Accepted> save(
            @PathVariable @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String id,
            @RequestBody @Valid SaveGreeting body,
            @RequestHeader(name = "Idempotency-Key", required = false) @Size(max = 200) String idempotencyKey,
            Principal caller) {
        String message = service.validate(body);
        // Record level: the caller's tenant. An update of an existing greeting would load its
        // tenant from the read model first: new Resource("greeting", id, existing.tenant()).
        String tenant = callers.tenant(caller);
        access.require(caller, new Resource("greeting", id, tenant), Action.WRITE);
        Actor actor = callers.actor(caller);
        auditor.record("greeting.save", actor, "greeting", List.of(id));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("tenant", tenant); // workers keep it, so reads can be limited to the caller's tenant
        data.put("name", body.name().strip());
        data.put("message", message);
        data.put("saved_by", actor.id());
        Events.Published published = events.publish("greetings", "saved", id, data, idempotencyKey);
        return ResponseEntity.accepted().body(Accepted.of(published.eventId()));
    }
}
