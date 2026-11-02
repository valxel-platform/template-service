package com.vrixel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs against a deployed environment (BASE_URL) after the Vrixel SDLC deploys a commit: as the
 * integration suite in dev, end-to-end in staging and smoke in production (post-deploy workflow).
 * Add API checks here; they get a token from the product's identity provider via API_TOKEN.
 */
@Tag("deployed")
class DeployedSmokeTest {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String baseUrl = System.getenv("BASE_URL");

    @Test
    void isReady() throws Exception {
        assumeTrue(baseUrl != null && !baseUrl.isBlank(), "BASE_URL is not set");
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health/readiness")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
    }

    @Test
    void refusesAnonymousApiCalls() throws Exception {
        assumeTrue(baseUrl != null && !baseUrl.isBlank(), "BASE_URL is not set");
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/greetings?name=smoke")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(401);
    }
}
