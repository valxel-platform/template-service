package com.vrixel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Probes and metrics are on the internal management port, open to the platform; the API port
 * serves only the API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ManagementPortTest {

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    Environment env;

    private final HttpClient http = HttpClient.newHttpClient();

    private int get(String port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + env.getProperty(port) + path)).build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    @Test
    void healthAndMetricsAreOnTheManagementPortOnly() throws Exception {
        assertThat(get("local.management.port", "/actuator/health/liveness")).isEqualTo(200);
        assertThat(get("local.management.port", "/actuator/prometheus")).isEqualTo(200);
        assertThat(get("local.server.port", "/actuator/prometheus")).isIn(401, 403, 404);
        assertThat(get("local.server.port", "/api/v1/greetings?name=Ada")).isEqualTo(401);
    }
}
