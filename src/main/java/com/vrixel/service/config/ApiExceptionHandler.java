package com.vrixel.service.config;

import com.vrixel.service.audit.AuditUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
}
