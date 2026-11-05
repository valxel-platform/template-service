package com.vrixel.service.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/** Scopes become SCOPE_<scope>; Keycloak realm roles (realm_access.roles) become ROLE_<role>. */
public class KeycloakAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<GrantedAuthority> out = new ArrayList<>(scopes.convert(jwt));
        if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> realm
                && realm.get("roles") instanceof Collection<?> roles) {
            for (Object role : roles) {
                out.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return out;
    }
}
