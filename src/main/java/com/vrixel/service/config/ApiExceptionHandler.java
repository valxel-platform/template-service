package com.vrixel.service.config;

import com.vrixel.service.audit.AuditUnavailableException;
import com.vrixel.service.events.EventsUnavailableException;
import com.vrixel.service.security.OutboundTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error is an RFC 9457 problem detail (application/problem+json). Validation errors come
 * from the base class. Messages never echo request data, so PHI cannot leak into responses or logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** No data and no success unless the access is on record (see com.vrixel.service.audit). */
    @ExceptionHandler(AuditUnavailableException.class)
    public ProblemDetail auditUnavailable(AuditUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "The audit trail is unavailable, so the request was not completed. Try again.");
    }

    /** The change is not on record in Kafka, so it did not happen (see com.vrixel.service.events). */
    @ExceptionHandler(EventsUnavailableException.class)
    public ProblemDetail eventsUnavailable(EventsUnavailableException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The change could not be recorded, so nothing changed. Try again with the same Idempotency-Key.");
    }

    /** A record-level refusal (AccessPolicy): 403, never saying whose record it is. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Not allowed.");
    }

    /** A downstream call couldn't be authorized on the caller's behalf, so it wasn't made. */
    @ExceptionHandler(OutboundTokenException.class)
    public ProblemDetail downstreamUnauthorized(OutboundTokenException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "A service this request needs could not be called on your behalf. Try again.");
    }
}
