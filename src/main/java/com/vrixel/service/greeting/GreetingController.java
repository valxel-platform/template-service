package com.vrixel.service.greeting;

import jakarta.validation.constraints.NotBlank;
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

    public GreetingController(GreetingService service) {
        this.service = service;
    }

    @GetMapping
    public Greeting greet(@RequestParam @NotBlank @Size(max = 100) String name) {
        return service.greet(name);
    }
}
