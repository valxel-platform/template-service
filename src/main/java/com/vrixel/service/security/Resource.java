package com.vrixel.service.security;

/**
 * A record a caller wants to read or change: its type, id and the tenant (clinic, merchant, farm)
 * it belongs to. Load the tenant from your read model before checking a read or an update of an
 * existing record; for a new record it is the caller's own.
 */
public record Resource(String type, String id, String tenant) {

    public Resource {
        if (type == null || type.isBlank() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("a resource needs a type and an id");
        }
    }
}
