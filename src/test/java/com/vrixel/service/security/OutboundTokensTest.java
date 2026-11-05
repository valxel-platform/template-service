package com.vrixel.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Forward inside the product; exchange across trust boundaries, once per caller and target. */
class OutboundTokensTest {

    private HttpServer keycloak;
    private final List<Map<String, String>> requests = new ArrayList<>();
    private final List<String> authHeaders = new ArrayList<>();
    private final AtomicReference<String> reply = new AtomicReference<>("{\"access_token\":\"exchanged-1\",\"expires_in\":300}");
    private final AtomicReference<Integer> status = new AtomicReference<>(200);
    private Instant now = Instant.parse("2026-11-05T12:00:00Z");

    @TempDir
    Path dir;

    @BeforeEach
    void start() throws Exception {
        keycloak = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        keycloak.createContext("/token", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = new HashMap<>();
            for (String pair : body.split("&")) {
                String[] kv = pair.split("=", 2);
                form.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
            requests.add(form);
            authHeaders.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            byte[] out = reply.get().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status.get(), out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        keycloak.start();
    }

    @AfterEach
    void stop() {
        keycloak.stop(0);
        SecurityContextHolder.clearContext();
    }

    private String tokenUri() {
        return "http://localhost:" + keycloak.getAddress().getPort() + "/token";
    }

    private OutboundTokens tokens(String secret, String keyFile) {
        var props = new OutboundProperties(tokenUri(), "emr-intake-api", secret, keyFile, "k1", Map.of(
                "chart", new OutboundProperties.Target(OutboundProperties.Mode.FORWARD, null, null),
                "consent", new OutboundProperties.Target(OutboundProperties.Mode.EXCHANGE, "ss-consent-api", "consents:read")));
        Clock clock = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        };
        return new OutboundTokens(props, HttpClient.newHttpClient(), clock);
    }

    private static JwtAuthenticationToken caller(String value) {
        return new JwtAuthenticationToken(Jwt.withTokenValue(value).header("alg", "RS256").subject("nurse-42")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
    }

    @Test
    void forwardsTheCallersTokenInsideTheProduct() {
        assertThat(tokens("s", "").tokenFor("chart", caller("user-token"))).isEqualTo("user-token");
        assertThat(requests).isEmpty();
    }

    @Test
    void exchangesAcrossATrustBoundaryAndCaches() {
        OutboundTokens t = tokens("s3cret", "");
        assertThat(t.tokenFor("consent", caller("user-token"))).isEqualTo("exchanged-1");
        assertThat(t.tokenFor("consent", caller("user-token"))).isEqualTo("exchanged-1");
        assertThat(requests).hasSize(1); // cached for this caller and target
        Map<String, String> form = requests.getFirst();
        assertThat(form).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                .containsEntry("subject_token", "user-token")
                .containsEntry("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
                .containsEntry("audience", "ss-consent-api")
                .containsEntry("scope", "consents:read");
        assertThat(authHeaders.getFirst()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("emr-intake-api:s3cret".getBytes(StandardCharsets.UTF_8)));

        t.tokenFor("consent", caller("another-user"));
        assertThat(requests).hasSize(2); // a different caller never gets someone else's token

        now = now.plusSeconds(280); // within 30s of expiry: exchange again
        reply.set("{\"access_token\":\"exchanged-2\",\"expires_in\":300}");
        assertThat(t.tokenFor("consent", caller("user-token"))).isEqualTo("exchanged-2");
    }

    @Test
    void authenticatesWithASignedAssertionWhenItHasAKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        Path pem = dir.resolve("key.pem");
        Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        tokens("", pem.toString()).tokenFor("consent", caller("user-token"));
        Map<String, String> form = requests.getFirst();
        assertThat(form).containsEntry("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                .containsEntry("client_id", "emr-intake-api");
        assertThat(authHeaders.getFirst()).isEqualTo("null"); // no shared secret on the wire
        SignedJWT assertion = SignedJWT.parse(form.get("client_assertion"));
        assertThat(assertion.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(
                (java.security.interfaces.RSAPublicKey) pair.getPublic()))).isTrue();
        assertThat(assertion.getJWTClaimsSet().getIssuer()).isEqualTo("emr-intake-api");
        assertThat(assertion.getJWTClaimsSet().getAudience()).containsExactly(tokenUri());
        assertThat(assertion.getHeader().getKeyID()).isEqualTo("k1");
    }

    @Test
    void neverActsWithoutTheUser() {
        assertThatThrownBy(() -> tokens("s", "").tokenFor("consent", null))
                .isInstanceOf(OutboundTokenException.class)
                .hasMessageContaining("needs the user's token");
        assertThatThrownBy(() -> tokens("s", "").tokenFor("billing", caller("t")))
                .hasMessageContaining("app.outbound.targets.billing");
        assertThatThrownBy(() -> tokens("", "").tokenFor("consent", caller("t")))
                .hasMessageContaining("client-key-file");
    }

    @Test
    void aRefusedExchangeSaysWhyButNeverEchoesTokens() {
        status.set(400);
        reply.set("{\"error\":\"invalid_target\",\"error_description\":\"user-token is not for you\"}");
        assertThatThrownBy(() -> tokens("s", "").tokenFor("consent", caller("user-token")))
                .isInstanceOf(OutboundTokenException.class)
                .hasMessage("token exchange for consent was refused (400, invalid_target)");
        status.set(200);
        reply.set("{}");
        assertThatThrownBy(() -> tokens("s", "").tokenFor("consent", caller("user-token")))
                .hasMessageContaining("no access_token");
    }

    @Test
    void addsTheBearerToEveryRequest() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(caller("user-token"));
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://chart/api"));
        new BearerFor("chart", tokens("s", "")).intercept(request, new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        assertThat(request.getHeaders().getFirst("Authorization")).isEqualTo("Bearer user-token");
    }
}
