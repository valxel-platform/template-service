package com.vrixel.service.config;

/**
 * A request that breaks a business rule. Its message is written for the caller and goes back in
 * the 400 response, so it states the rule and never echoes the input. Any other
 * IllegalArgumentException (from a library, say) answers a generic 400: its message may hold data.
 */
public class InvalidRequestException extends IllegalArgumentException {

    public InvalidRequestException(String rule) {
        super(rule);
    }
}
