package com.vrixel.service.audit;

/** Who acted: a person ({@code user}), another service, or an agent. {@code client} is the OAuth client used. */
public record Actor(String id, String type, String client) {

    public Actor {
        id = id == null ? "" : id;
        type = type == null || type.isBlank() ? "user" : type;
        client = client == null ? "" : client;
    }

    public static Actor user(String id) {
        return new Actor(id, "user", "");
    }

    public static Actor service(String id) {
        return new Actor(id, "service", "");
    }
}
