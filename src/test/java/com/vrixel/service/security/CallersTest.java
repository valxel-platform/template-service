package com.vrixel.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vrixel.service.audit.Actor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CallersTest {

    static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token-value").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    private final Callers callers = new Callers("organization");

    @Test
    void namesTheUserAndTheClientActingForThem() {
        Actor a = callers.actor(new JwtAuthenticationToken(jwt(Map.of("azp", "emr-intake-api"))));
        assertThat(a).isEqualTo(new Actor("user-1", "user", "emr-intake-api"));
        assertThat(callers.actor(null).id()).isEqualTo("anonymous");
    }

    @Test
    void readsTheTenantInKeycloaksShapes() {
        assertThat(callers.tenant(new JwtAuthenticationToken(jwt(Map.of("organization", "acme"))))).isEqualTo("acme");
        assertThat(callers.tenant(new JwtAuthenticationToken(jwt(Map.of("organization", Map.of("acme", Map.of()))))))
                .isEqualTo("acme");
        assertThat(callers.tenant(new JwtAuthenticationToken(jwt(Map.of("organization", List.of("acme")))))).isEqualTo("acme");
        // Two tenants in one token: ambiguous, so none.
        assertThat(callers.tenant(new JwtAuthenticationToken(jwt(Map.of("organization", List.of("a", "b")))))).isNull();
        assertThat(callers.tenant(null)).isNull();
        assertThat(new Callers(" ").tenant(new JwtAuthenticationToken(jwt(Map.of("tenant", "t"))))).isEqualTo("t");
    }

    @Test
    void keepsTenantsApart() {
        TenantAccessPolicy policy = new TenantAccessPolicy(callers);
        var acme = new JwtAuthenticationToken(jwt(Map.of("organization", "acme")));
        policy.require(acme, new Resource("patient", "p-1", "acme"), Action.READ);
        policy.require(acme, new Resource("patient", "p-2", null), Action.WRITE); // a new record
        assertThatThrownBy(() -> policy.require(acme, new Resource("patient", "p-3", "globex"), Action.READ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("not this caller's patient");
        var none = new JwtAuthenticationToken(jwt(Map.of()));
        assertThatThrownBy(() -> policy.require(none, new Resource("patient", "p-1", "acme"), Action.READ))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> new Resource("patient", " ", "acme")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scopesAndRealmRolesBecomeAuthorities() {
        Jwt token = jwt(Map.of("scope", "greetings:read greetings:write", "realm_access", Map.of("roles", List.of("clinician"))));
        assertThat(new KeycloakAuthorities().convert(token)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_greetings:read", "SCOPE_greetings:write", "ROLE_clinician");
        assertThat(new KeycloakAuthorities().convert(jwt(Map.of()))).isEmpty();
    }

    @Test
    void settingsHaveSafeDefaults() {
        SecurityProperties p = new SecurityProperties(null, null, " emr-api ", null);
        assertThat(p.enabled()).isTrue();
        assertThat(p.issuer()).isEmpty();
        assertThat(p.audience()).isEqualTo("emr-api");
        assertThat(p.tenantClaim()).isEqualTo("tenant");
    }
}
