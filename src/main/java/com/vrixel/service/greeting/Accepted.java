package com.vrixel.service.greeting;

/**
 * Response body of a command (202 Accepted): the change is on record in Kafka and the event
 * workers will apply it. {@code eventId} lets the caller find when it has been applied.
 */
public record Accepted(String eventId, String status) {

    public static Accepted of(String eventId) {
        return new Accepted(eventId, "accepted");
    }
}
