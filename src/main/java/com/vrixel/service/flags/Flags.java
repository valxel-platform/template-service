package com.vrixel.service.flags;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ImmutableContext;
import org.springframework.stereotype.Component;

/**
 * Feature flags. The Vrixel SDLC tool gives each feature a flag (a key like
 * {@code emr.emr-feat-3.check-in-kiosk}) in the product's Flipt when its design is approved: on in
 * dev and staging, off in production until the feature is released. Put the new behavior behind it:
 *
 * <pre>{@code
 * if (flags.enabled("emr.emr-feat-3.check-in-kiosk", user)) {
 *     // the new behavior
 * } else {
 *     // today's behavior
 * }
 * }</pre>
 */
@Component
public class Flags {

    private final Client client;

    public Flags(Client client) {
        this.client = client;
    }

    /**
     * Whether the feature is on for this caller (their user id: a gradual release turns it on for a
     * share of users, and each keeps the same answer). The request counts toward the flag's on or
     * off side ({@link FlagCohorts}).
     */
    public boolean enabled(String key, String user) {
        boolean on = Boolean.TRUE.equals(client.getBooleanValue(key, false, new ImmutableContext(user == null ? "" : user)));
        FlagCohorts.seen(key, on);
        return on;
    }

    public boolean enabled(String key) {
        return enabled(key, "");
    }
}
