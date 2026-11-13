package com.valxel.service.security;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Calls this service makes to others (app.outbound.*), and how each carries the user:
 *
 * <pre>
 * app.outbound:
 *   token-uri: https://id.example.com/realms/emr/protocol/openid-connect/token
 *   client-id: emr-intake-api
 *   client-key-file: /var/run/secrets/client/key.pem   # private_key_jwt (preferred), or client-secret
 *   targets:
 *     chart:   { mode: forward }                                     # same product: the mesh proves the caller
 *     consent: { mode: exchange, audience: ss-consent-api, scope: consents:read }   # another trust domain
 * </pre>
 *
 * @param tokenUri the realm's token endpoint
 * @param clientId this service's client in the realm
 * @param clientSecret client_secret_basic, if there is no key file
 * @param clientKeyFile PEM (PKCS#8) RSA key for private_key_jwt client authentication
 * @param clientKeyId the key's kid, as registered with the client
 * @param targets per downstream service: forward the caller's token, or exchange it
 */
@ConfigurationProperties("app.outbound")
public record OutboundProperties(
        String tokenUri,
        String clientId,
        String clientSecret,
        String clientKeyFile,
        String clientKeyId,
        Map<String, Target> targets) {

    /** How a call to one downstream service is authorized. */
    public enum Mode {
        /** Inside the product: pass the caller's token on (its audience is the product). */
        FORWARD,
        /** Across a trust boundary: exchange it for a token for that service only (RFC 8693). */
        EXCHANGE
    }

    /**
     * @param mode forward or exchange
     * @param audience the target's client id (exchange)
     * @param scope the scopes the call needs, space-separated (exchange)
     */
    public record Target(Mode mode, String audience, String scope) {
        public Target {
            mode = mode == null ? Mode.FORWARD : mode;
            audience = audience == null ? "" : audience;
            scope = scope == null ? "" : scope;
        }
    }

    public OutboundProperties {
        tokenUri = tokenUri == null ? "" : tokenUri;
        clientId = clientId == null ? "" : clientId;
        clientSecret = clientSecret == null ? "" : clientSecret;
        clientKeyFile = clientKeyFile == null ? "" : clientKeyFile;
        clientKeyId = clientKeyId == null ? "" : clientKeyId;
        targets = targets == null ? Map.of() : Map.copyOf(targets);
    }
}
