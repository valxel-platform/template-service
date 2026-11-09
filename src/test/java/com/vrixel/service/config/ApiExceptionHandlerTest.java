package com.vrixel.service.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

/** Business rule violations from services become 400 problem details with the rule, not the input. */
class ApiExceptionHandlerTest {

    @Test
    void turnsRuleViolationsIntoBadRequests() {
        ProblemDetail problem = new ApiExceptionHandler()
                .badRequest(new InvalidRequestException("name must not be blank"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("name must not be blank");
    }

    @Test
    void otherBadArgumentsNeverEchoTheirMessage() {
        ProblemDetail problem = new ApiExceptionHandler()
                .badRequest(new IllegalArgumentException("Invalid date 1984-03-02 for Ana Lopez"));
        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("The request is not valid.");
    }
}
