package com.valxel.service.events;

/** Kafka didn't confirm the event, so the change did not happen and must not be reported as done. */
public class EventsUnavailableException extends RuntimeException {

    public EventsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
