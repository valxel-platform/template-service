package com.valxel.service.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.security.*: enabled (only the contract tests turn it off), the realm that issues tokens,
 * the audience this service accepts (the product's API, e.g. emr-api) and the claim naming the
 * caller's tenant.
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(Boolean enabled, String issuer, String audience, String tenantClaim) {

    public SecurityProperties {
        enabled = enabled == null || enabled;
        issuer = issuer == null ? "" : issuer.strip();
        audience = audience == null ? "" : audience.strip();
        tenantClaim = tenantClaim == null || tenantClaim.isBlank() ? "tenant" : tenantClaim.strip();
    }
}
