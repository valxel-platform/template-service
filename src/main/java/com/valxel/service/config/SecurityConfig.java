package com.valxel.service.config;

import com.valxel.service.audit.Actor;
import com.valxel.service.audit.Auditor;
import com.valxel.service.observability.Paths;
import com.valxel.service.security.AccessPolicy;
import com.valxel.service.security.Callers;
import com.valxel.service.security.KeycloakAuthorities;
import com.valxel.service.security.OutboundProperties;
import com.valxel.service.security.OutboundTokens;
import com.valxel.service.security.SecurityProperties;
import com.valxel.service.security.TenantAccessPolicy;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Secure by default (see com.valxel.service.security): every API call needs a JWT from the
 * product's Keycloak realm issued for this product (APP_AUDIENCE), every endpoint declares its
 * permission, and anything else is denied. Health probes stay open; metrics are on the internal
 * management port (MANAGEMENT_PORT), which no endpoint exposes. The only way to turn this off is
 * app.security.enabled=false, which the contract tests use; never set it in a deployed environment.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SecurityProperties.class, OutboundProperties.class})
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    SecurityFilterChain api(HttpSecurity http, Auditor auditor, Callers callers) throws Exception {
        JwtAuthenticationConverter tokens = new JwtAuthenticationConverter();
        tokens.setJwtGrantedAuthoritiesConverter(new KeycloakAuthorities());
        // Refusals are on record (outcome denied) without waiting for the audit trail: a missing or
        // invalid token (401), and a token without the permission an URL needs (403).
        AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        AuthenticationEntryPoint unauthenticated = (request, response, ex) -> {
            auditor.recordQuietly("access.unauthenticated", Actor.user("anonymous"), "denied",
                    ex.getClass().getSimpleName(), Map.of("method", request.getMethod(),
                            "endpoint", Paths.withoutIds(request.getRequestURI())));
            bearer.commence(request, response, ex);
        };
        AccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();
        AccessDeniedHandler denied = (request, response, ex) -> {
            auditor.recordQuietly("access.denied", callers.actor(SecurityContextHolder.getContext().getAuthentication()),
                    "denied", "not permitted", Map.of("method", request.getMethod(),
                            "endpoint", Paths.withoutIds(request.getRequestURI())));
            bearerDenied.handle(request, response, ex);
        };
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/**").authenticated() // then each endpoint's @PreAuthorize
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(tokens))
                        .authenticationEntryPoint(unauthenticated)
                        .accessDeniedHandler(denied))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthenticated).accessDeniedHandler(denied))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable()) // stateless bearer-token API: no cookies to protect
                .build();
    }

    /**
     * Tokens from the product's realm, checked against its published keys (rotated keys are
     * picked up by kid), its issuer, their expiry and this product's audience: a token issued for
     * another product or client is refused.
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    JwtDecoder jwtDecoder(SecurityProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(requireIssuer(props)).build();
        decoder.setJwtValidator(validator(props));
        return decoder;
    }

    static String requireIssuer(SecurityProperties props) {
        if (props.issuer().isBlank()) {
            throw new IllegalStateException("Set APP_ISSUER to the product's Keycloak realm, e.g. https://id.example.com/realms/emr");
        }
        if (props.audience().isBlank()) {
            throw new IllegalStateException("Set APP_AUDIENCE, e.g. emr-api: without it this service would "
                    + "accept tokens issued for any client of the realm");
        }
        return props.issuer();
    }

    static OAuth2TokenValidator<Jwt> validator(SecurityProperties props) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                new JwtClaimValidator<Collection<String>>(
                        JwtClaimNames.AUD, aud -> aud != null && aud.contains(props.audience())));
    }

    @Bean
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "false")
    SecurityFilterChain open(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    Callers callers(SecurityProperties props) {
        return new Callers(props.tenantClaim());
    }

    @Bean
    @ConditionalOnMissingBean(AccessPolicy.class)
    AccessPolicy accessPolicy(Callers callers) {
        return new TenantAccessPolicy(callers);
    }

    @Bean
    OutboundTokens outboundTokens(OutboundProperties props) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new OutboundTokens(props, http, Clock.systemUTC());
    }

    /** @PreAuthorize on every endpoint; off with security, so the contract tests reach the API. */
    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @ConditionalOnProperty(name = "app.security.enabled", havingValue = "true", matchIfMissing = true)
    static class MethodSecurity {}
}
