package com.vrixel.service.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The bearer token for a call to another service, on behalf of the current caller.
 *
 * <ul>
 *   <li><b>forward</b> (same product): the caller's own token. The mesh proves which service is
 *       calling; the token proves which user.
 *   <li><b>exchange</b> (another product, a shared service, the most sensitive services): the
 *       caller's token is exchanged at Keycloak (RFC 8693 token exchange) for one issued only for
 *       that service, with only the scopes this call needs. The user stays the subject; this
 *       service becomes the authorized party ({@code azp}). Exchanged tokens are cached per caller
 *       and target until shortly before they expire.
 * </ul>
 *
 * There is no fallback to this service's own credentials: a call on a user's behalf without a
 * user fails. Calls with no user behind them (scheduled jobs) use client credentials instead.
 */
public class OutboundTokens {

    private static final Duration EARLY = Duration.ofSeconds(30);
    private static final int MAX_CACHED = 10_000;

    private final OutboundProperties props;
    private final HttpClient http;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();
    private volatile PrivateKey clientKey;

    private record Cached(String token, Instant refreshAt) {}

    public OutboundTokens(OutboundProperties props, HttpClient http, Clock clock) {
        this.props = props;
        this.http = http;
        this.clock = clock;
    }

    public String tokenFor(String target, Authentication caller) {
        OutboundProperties.Target t = props.targets().get(target);
        if (t == null) {
            throw new IllegalStateException("No app.outbound.targets." + target + ": say whether to forward or exchange");
        }
        if (!(caller instanceof JwtAuthenticationToken jwt)) {
            throw new OutboundTokenException("a call to " + target + " on a user's behalf needs the user's token");
        }
        String subject = jwt.getToken().getTokenValue();
        if (t.mode() == OutboundProperties.Mode.FORWARD) {
            return subject;
        }
        String key = target + ":" + sha256(subject);
        Cached hit = cache.get(key);
        if (hit != null && clock.instant().isBefore(hit.refreshAt())) {
            return hit.token();
        }
        Cached fresh = exchange(target, t, subject);
        if (cache.size() >= MAX_CACHED) {
            cache.values().removeIf(c -> !clock.instant().isBefore(c.refreshAt()));
            if (cache.size() >= MAX_CACHED) {
                cache.clear();
            }
        }
        cache.put(key, fresh);
        return fresh.token();
    }

    private Cached exchange(String target, OutboundProperties.Target t, String subject) {
        if (props.tokenUri().isBlank() || props.clientId().isBlank()) {
            throw new IllegalStateException("Set app.outbound.token-uri and client-id to exchange tokens");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.put("subject_token", subject);
        form.put("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.put("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        if (!t.audience().isBlank()) {
            form.put("audience", t.audience());
        }
        if (!t.scope().isBlank()) {
            form.put("scope", t.scope());
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(props.tokenUri()))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json");
        if (!props.clientKeyFile().isBlank()) {
            form.put("client_id", props.clientId());
            form.put("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
            form.put("client_assertion", assertion());
        } else if (!props.clientSecret().isBlank()) {
            String basic = enc(props.clientId()) + ":" + enc(props.clientSecret());
            req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)));
        } else {
            throw new IllegalStateException("Set app.outbound.client-key-file (private_key_jwt) or client-secret");
        }
        String body = form.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        HttpResponse<String> res;
        try {
            res = http.send(req.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new OutboundTokenException("token exchange for " + target + " failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboundTokenException("token exchange for " + target + " was interrupted");
        }
        if (res.statusCode() != 200) {
            // The error code only: never the response body, which may echo the token.
            throw new OutboundTokenException("token exchange for " + target + " was refused (" + res.statusCode() + ", "
                    + error(res.body()) + ")");
        }
        JsonNode node = json.readTree(res.body());
        String token = node.path("access_token").asString("");
        if (token.isBlank()) {
            throw new OutboundTokenException("token exchange for " + target + " returned no access_token");
        }
        long expiresIn = node.path("expires_in").asLong(60);
        Instant refreshAt = clock.instant().plusSeconds(expiresIn).minus(EARLY);
        return new Cached(token, refreshAt);
    }

    private String error(String body) {
        try {
            return json.readTree(body).path("error").asString("unknown");
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /** private_key_jwt: a short-lived assertion signed with this service's key, for the token endpoint only. */
    private String assertion() {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(props.clientId())
                .subject(props.clientId())
                .audience(props.tokenUri())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build();
        JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256);
        if (!props.clientKeyId().isBlank()) {
            header.keyID(props.clientKeyId());
        }
        SignedJWT jwt = new SignedJWT(header.build(), claims);
        try {
            jwt.sign(new RSASSASigner(key()));
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign the client assertion", e);
        }
        return jwt.serialize();
    }

    private PrivateKey key() {
        if (clientKey == null) {
            try {
                String pem = Files.readString(Path.of(props.clientKeyFile()))
                        .replaceAll("-----[A-Z ]+-----", "")
                        .replaceAll("\\s", "");
                clientKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
            } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException("could not read the client key (PKCS#8 PEM) at app.outbound.client-key-file", e);
            }
        }
        return clientKey;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
