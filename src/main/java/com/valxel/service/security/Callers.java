package com.valxel.service.security;

import com.valxel.service.audit.Actor;
import java.security.Principal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * What the token says about the caller: the user ({@code sub}), the client that asked for or
 * exchanged the token ({@code azp}), and the tenant. Audit records name both the user and the
 * client, so a call made by a service on a user's behalf shows who acted for whom.
 */
public class Callers {

    private final String tenantClaim;

    public Callers(String tenantClaim) {
        this.tenantClaim = tenantClaim == null || tenantClaim.isBlank() ? "tenant" : tenantClaim;
    }

    public Actor actor(Principal caller) {
        if (caller instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            return new Actor(jwt.getSubject(), "user", text(jwt, "azp"));
        }
        return Actor.user(caller == null ? "anonymous" : caller.getName());
    }

    public String tenant(Principal caller) {
        if (caller instanceof JwtAuthenticationToken token) {
            Object v = token.getToken().getClaims().get(tenantClaim);
            if (v instanceof String s) {
                return s;
            }
            // Keycloak organizations: {"organization": {"acme": {...}}} or ["acme"]; one tenant per token.
            if (v instanceof java.util.Map<?, ?> m && m.size() == 1) {
                return String.valueOf(m.keySet().iterator().next());
            }
            if (v instanceof java.util.Collection<?> c && c.size() == 1) {
                return String.valueOf(c.iterator().next());
            }
        }
        return null;
    }

    private static String text(Jwt jwt, String claim) {
        Object v = jwt.getClaims().get(claim);
        return v == null ? "" : v.toString();
    }
}
