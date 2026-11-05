/**
 * Who may call this service, and what they may do (constitution VX-17).
 *
 * <p>Every request is checked three times, in this order:
 *
 * <ol>
 *   <li><b>Who</b>: a JWT from the product's Keycloak realm, signed by a key in its JWKS, not
 *       expired, and issued <em>for this product</em> ({@code aud} contains APP_AUDIENCE, e.g.
 *       {@code emr-api}). A token for another product or service is refused (401).
 *   <li><b>What</b>: every endpoint declares the permission it needs with {@code @PreAuthorize}
 *       (scopes become {@code SCOPE_<scope>}, Keycloak realm roles {@code ROLE_<role>}). An endpoint
 *       without one is denied, and {@code EndpointPermissionsTest} fails the build.
 *   <li><b>Which record</b>: {@link com.vrixel.service.security.AccessPolicy} decides whether this
 *       caller may read or change this record. The default keeps tenants apart; plug in OpenFGA
 *       or OPA for relationship rules (a clinician and their patients).
 * </ol>
 *
 * <p>Inside the product, the calling service is proven by the mesh (Istio ambient mTLS) and the
 * user's token is forwarded. Calls to another product, a shared service or the most sensitive
 * services exchange it for a narrower token ({@link com.vrixel.service.security.OutboundTokens}).
 */
package com.vrixel.service.security;
