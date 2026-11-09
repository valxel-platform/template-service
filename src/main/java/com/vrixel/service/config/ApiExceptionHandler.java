package com.vrixel.service.config;

import com.vrixel.service.audit.AuditUnavailableException;
import com.vrixel.service.audit.Auditor;
import com.vrixel.service.security.Callers;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;
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

    private final ObjectProvider<Auditor> auditor;
    private final ObjectProvider<Callers> callers;

    /** Without an audit trail (unit tests of the handler alone). */
    public ApiExceptionHandler() {
        this(null, null);
    }

    @Autowired
    public ApiExceptionHandler(ObjectProvider<Auditor> auditor, ObjectProvider<Callers> callers) {
        this.auditor = auditor;
        this.callers = callers;
    }

    /** A broken business rule says which rule; any other bad argument says nothing about the input. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail badRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex instanceof InvalidRequestException ? ex.getMessage() : "The request is not valid.");
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

    /**
     * A refusal (a missing permission, or a record-level AccessPolicy refusal): 403, never saying
     * whose record it is. The refusal is on record (outcome denied): who tried which endpoint.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail forbidden(AccessDeniedException ex, HttpServletRequest request) {
        auditDenied(request);
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Not allowed.");
    }

    private void auditDenied(HttpServletRequest request) {
        Auditor a = auditor == null ? null : auditor.getIfAvailable();
        Callers c = callers == null ? null : callers.getIfAvailable();
        if (a == null || c == null) {
            return;
        }
        a.recordQuietly("access.denied", c.actor(SecurityContextHolder.getContext().getAuthentication()), "denied",
                "not permitted", endpoint(request));
    }

    /** The endpoint as declared (/api/v1/greetings/{id}), never the ids or the query string. */
    public static Map<String, String> endpoint(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return Map.of("method", request.getMethod(), "endpoint",
                pattern == null ? com.vrixel.service.observability.Paths.withoutIds(request.getRequestURI()) : pattern.toString());
    }

    /** A downstream call couldn't be authorized on the caller's behalf, so it wasn't made. */
    @ExceptionHandler(OutboundTokenException.class)
    public ProblemDetail downstreamUnauthorized(OutboundTokenException ex) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "A service this request needs could not be called on your behalf. Try again.");
    }
}
