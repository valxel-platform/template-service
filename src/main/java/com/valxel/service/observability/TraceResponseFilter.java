package com.valxel.service.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every response says which trace it belongs to, in a W3C {@code traceresponse} header
 * ({@code 00-<trace id>-<span id>-01}). The web and mobile apps put it on the errors they report,
 * so the SDLC tool can tell that a failure people saw and an error here are the same incident, and
 * on-call can open the request's trace and logs in OpenChoreo from either.
 */
public class TraceResponseFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER = "traceresponse";

    private final ObjectProvider<Tracer> tracer;

    public TraceResponseFilter(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    /** The current span as a traceresponse value, or null outside a trace. */
    public static String value(Tracer tracer) {
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span == null || span.context().traceId().isBlank()) {
            return null;
        }
        return "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = value(tracer.getIfAvailable());
        if (header != null) {
            response.setHeader(HEADER, header);
        }
        chain.doFilter(request, response);
    }

    /** After the server observation starts the request's span (it runs at HIGHEST_PRECEDENCE + 1). */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
