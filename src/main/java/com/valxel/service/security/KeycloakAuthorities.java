package com.valxel.service.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Scopes become SCOPE_<scope>; Keycloak realm roles (realm_access.roles) become ROLE_<role>.
 *
 * <p>A permission scope ({@code greetings:write}) counts only if the person holds it too: the
 * product's realm puts the person's permissions in a {@code permissions} claim, and a scope the app
 * asked for but the person doesn't hold is dropped. What the app may do and what the person may do
 * must both allow it. Tokens without the claim (service accounts in realms that don't add it) keep
 * their scopes.
 */
public class KeycloakAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> out = new ArrayList<>(scopes.convert(jwt));
        if (jwt.getClaims().get("permissions") instanceof Collection<?> held) {
            out.removeIf(a -> {
                String scope = a.getAuthority().substring("SCOPE_".length());
                return scope.contains(":") && !held.contains(scope);
            });
        }
        if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> realm
                && realm.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                out.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return out;
    }
}
