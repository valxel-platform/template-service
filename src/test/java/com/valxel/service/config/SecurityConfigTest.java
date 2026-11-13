package com.valxel.service.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.valxel.service.security.SecurityProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/** Tokens are accepted only from the product's realm and only when issued for this product. */
class SecurityConfigTest {

    private static final String ISSUER = "https://id.example.com/realms/emr";
    private final OAuth2TokenValidator<Jwt> validator =
            SecurityConfig.validator(new SecurityProperties(true, ISSUER, "emr-api", null));

    private static Jwt token(String issuer, List<String> aud) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuer(issuer).audience(aud)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void acceptsOnlyThisProductsAudience() {
        assertThat(validator.validate(token(ISSUER, List.of("emr-api", "account"))).hasErrors()).isFalse();
        assertThat(validator.validate(token(ISSUER, List.of("pos-api"))).hasErrors()).isTrue();
        assertThat(validator.validate(token(ISSUER, List.of())).hasErrors()).isTrue();
        assertThat(validator.validate(token("https://id.example.com/realms/pos", List.of("emr-api"))).hasErrors()).isTrue();
    }

    @Test
    void refusesToStartWithoutRealmAndAudience() {
        assertThatThrownBy(() -> SecurityConfig.requireIssuer(new SecurityProperties(true, "", "emr-api", null)))
                .hasMessageContaining("APP_ISSUER");
        assertThatThrownBy(() -> SecurityConfig.requireIssuer(new SecurityProperties(true, ISSUER, "", null)))
                .hasMessageContaining("APP_AUDIENCE");
        assertThat(SecurityConfig.requireIssuer(new SecurityProperties(true, ISSUER, "emr-api", null))).isEqualTo(ISSUER);
    }
}
