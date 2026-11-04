package com.vrixel.service.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vrixel.service.audit.RecordingSink;
import com.vrixel.service.config.ApiExceptionHandler;
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

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                        new GreetingController(new GreetingService(), RecordingSink.auditor(audit, true)))
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
}
