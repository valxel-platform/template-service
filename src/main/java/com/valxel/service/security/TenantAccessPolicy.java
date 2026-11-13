package com.valxel.service.security;

import java.security.Principal;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;

/**
 * The default record policy: a caller works only on records of their own tenant (the token's
 * tenant claim, app.security.tenant-claim, e.g. Keycloak's organization). A caller without a
 * tenant, or a record of another tenant, is refused. Products with finer rules (care teams,
 * delegated access) replace this bean with one that asks OpenFGA or OPA.
 */
public class TenantAccessPolicy implements AccessPolicy {

    private final Callers callers;

    public TenantAccessPolicy(Callers callers) {
        this.callers = callers;
    }

    @Override
    public void require(Principal caller, Resource resource, Action action) {
        String tenant = callers.tenant(caller);
        if (tenant == null || tenant.isBlank()) {
            throw new AccessDeniedException("the caller belongs to no tenant");
        }
        if (resource.tenant() != null && !Objects.equals(tenant, resource.tenant())) {
            // Never say whose record it is: 403 with no detail.
            throw new AccessDeniedException("not this caller's " + resource.type());
        }
    }
}
