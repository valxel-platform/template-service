package com.valxel.service.security;

/** No token for a downstream call: the call must not be made. Messages never contain tokens. */
public class OutboundTokenException extends RuntimeException {

    public OutboundTokenException(String message) {
        super(message);
    }
}
