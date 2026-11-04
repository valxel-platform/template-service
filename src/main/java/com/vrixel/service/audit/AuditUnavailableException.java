package com.vrixel.service.audit;

/** The audit record could not be published, so the action must not be reported as done. */
public class AuditUnavailableException extends RuntimeException {

    public AuditUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
