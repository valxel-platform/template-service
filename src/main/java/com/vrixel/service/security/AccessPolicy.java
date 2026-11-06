package com.vrixel.service.security;

import java.security.Principal;

/**
 * Record-level authorization: may this caller do this to this record? Call it in every handler
 * that touches a record, after the endpoint's permission check. Deny by throwing
 * {@link org.springframework.security.access.AccessDeniedException} (403).
 */
public interface AccessPolicy {

    void require(Principal caller, Resource resource, Action action);
}
