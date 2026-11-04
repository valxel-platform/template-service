package com.vrixel.service.greeting;

import com.vrixel.service.audit.Actor;
import com.vrixel.service.audit.Auditor;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
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

    public GreetingController(GreetingService service, Auditor auditor) {
        this.service = service;
        this.auditor = auditor;
    }

    /**
     * Audited like any read of product data: who asked, never what they asked about (the name could
     * be PHI). Replace with your resource type and record ids.
     */
    @GetMapping
    public Greeting greet(@RequestParam @NotBlank @Size(max = 100) String name, Principal caller) {
        Greeting greeting = service.greet(name);
        auditor.record("greeting.read", Actor.user(caller == null ? "anonymous" : caller.getName()), "greeting", List.of());
        return greeting;
    }
}
