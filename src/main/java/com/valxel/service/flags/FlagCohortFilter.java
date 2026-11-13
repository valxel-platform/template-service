package com.valxel.service.flags;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/** Counts each request for the flags it evaluated: failed on a 5xx or an exception ({@link FlagCohorts}). */
public class FlagCohortFilter extends OncePerRequestFilter implements Ordered {

    private final FlagCohorts cohorts;

    public FlagCohortFilter(FlagCohorts cohorts) {
        this.cohorts = cohorts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        FlagCohorts.begin();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            failed = true;
            throw e;
        } finally {
            cohorts.end(failed || response.getStatus() >= 500);
        }
    }

    /** Around everything else, so an error handler's 500 counts as failed. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}
