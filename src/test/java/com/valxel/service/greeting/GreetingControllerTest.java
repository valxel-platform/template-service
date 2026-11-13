package com.valxel.service.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.valxel.service.audit.RecordingSink;
import com.valxel.service.config.ApiExceptionHandler;
import com.valxel.service.events.RecordingEventSink;
import com.valxel.service.security.Callers;
import com.valxel.service.security.TenantAccessPolicy;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** The web layer alone: routing, validation and error format, without starting the application. */
class GreetingControllerTest {

    private MockMvc mvc;
    private final RecordingSink audit = new RecordingSink();
    private final RecordingEventSink events = new RecordingEventSink();
    private final Callers callers = new Callers("tenant");

    /** A user of clinic-1, calling through the BFF (azp). */
    static JwtAuthenticationToken user(String tenant) {
        Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .claim("azp", "emr-bff").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (tenant != null) {
            jwt.claim("tenant", tenant);
        }
        return new JwtAuthenticationToken(jwt.build());
    }

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                        new GreetingController(
                                new GreetingService(), RecordingSink.auditor(audit, true), RecordingEventSink.events(events),
                                callers, new TenantAccessPolicy(callers)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsAGreeting() throws Exception {
        mvc.perform(get("/api/v1/greetings").param("name", "Ada").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Hello, Ada!"));
        assertThat(audit.events).hasSize(1);
        assertThat(audit.events.getFirst().read("$.data.action", String.class)).isEqualTo("greeting.read");
        assertThat(audit.events.getFirst().read("$.data.actor.id", String.class)).isEqualTo("anonymous");
        assertThat(audit.events.getFirst().jsonString()).doesNotContain("Ada"); // never what was asked
    }

    @Test
    void answersNothingWhenTheReadCantBeAudited() throws Exception {
        audit.fail = true;
        mvc.perform(get("/api/v1/greetings").param("name", "Ada"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "The audit trail is unavailable, so the request was not completed. Try again."));
    }

    @Test
    void rejectsNamesThatAreTooLong() throws Exception {
        mvc.perform(get("/api/v1/greetings").param("name", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void requiresAName() throws Exception {
        mvc.perform(get("/api/v1/greetings")).andExpect(status().isBadRequest());
    }

    private static final String BODY = "{\"name\":\"Ada\",\"message\":\"Welcome back, Ada\"}";

    @Test
    void aCommandIsAnEventOnKafkaAndIsAccepted() throws Exception {
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("Idempotency-Key", "k1").principal(user("clinic-1")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").value(events.sent.getFirst().headers().get("ce_id")));
        RecordingEventSink.Sent sent = events.sent.getFirst();
        assertThat(sent.topic()).isEqualTo("emr.intake-api.greetings.v1");
        assertThat(sent.key()).isEqualTo("g-1");
        assertThat(sent.event().read("$.type", String.class)).isEqualTo("valxel.greeting.saved.v1");
        assertThat(sent.event().read("$.data.message", String.class)).isEqualTo("Welcome back, Ada");
        // Audited by id only: the record never holds the name or the message.
        assertThat(audit.events.getFirst().read("$.data.action", String.class)).isEqualTo("greeting.save");
        assertThat(audit.events.getFirst().jsonString()).contains("g-1").doesNotContain("Ada");
        // Who acted for whom: the user, and the client that called on their behalf.
        assertThat(audit.events.getFirst().read("$.data.actor.id", String.class)).isEqualTo("user-1");
        assertThat(audit.events.getFirst().read("$.data.actor.client", String.class)).isEqualTo("emr-bff");
        assertThat(sent.event().read("$.data.tenant", String.class)).isEqualTo("clinic-1");
    }

    @Test
    void aCallerWithoutATenantChangesNothing() throws Exception {
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .principal(user(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Not allowed."));
        assertThat(events.sent).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void whenKafkaIsDownNothingChangesAndTheCallerRetries() throws Exception {
        events.fail = true;
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .principal(user("clinic-1")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "The change could not be recorded, so nothing changed. Try again with the same Idempotency-Key."));
    }

    @Test
    void anInvalidCommandPublishesNothing() throws Exception {
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"message\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/greetings/Not_An_Id").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest());
        assertThat(events.sent).isEmpty();
    }
}
