package com.vrixel.service.security;

import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Adds the right bearer token to every request of a RestClient for one downstream service:
 *
 * <pre>
 * RestClient chart = RestClient.builder()
 *         .baseUrl(System.getenv("CHART_API_URL"))       // from workload.yaml dependencies
 *         .requestInterceptor(new BearerFor("chart", outboundTokens))
 *         .build();
 * </pre>
 */
public class BearerFor implements ClientHttpRequestInterceptor {

    private final String target;
    private final OutboundTokens tokens;

    public BearerFor(String target, OutboundTokens tokens) {
        this.target = target;
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String token = tokens.tokenFor(target, SecurityContextHolder.getContext().getAuthentication());
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return execution.execute(request, body);
    }
}
