package com.vrixel.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/** Business rule violations from services become 400 problem details with the rule, not the input. */
class ApiExceptionHandlerTest {

    @Test
    void turnsRuleViolationsIntoBadRequests() {
        ProblemDetail problem = new ApiExceptionHandler()
                .badRequest(new IllegalArgumentException("name must not be blank"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("name must not be blank");
    }
}
