package com.vrixel.service.config;

import com.vrixel.service.security.AccessPolicy;
import com.vrixel.service.security.Callers;
import com.vrixel.service.security.KeycloakAuthorities;
import com.vrixel.service.security.OutboundProperties;
import com.vrixel.service.security.OutboundTokens;
import com.vrixel.service.security.SecurityProperties;
import com.vrixel.service.security.TenantAccessPolicy;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
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
 * Secure by default (see com.vrixel.service.security): every API call needs a JWT from the
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
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        JwtAuthenticationConverter tokens = new JwtAuthenticationConverter();
        tokens.setJwtGrantedAuthoritiesConverter(new KeycloakAuthorities());
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/api/**").authenticated() // then each endpoint's @PreAuthorize
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(tokens)))
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
