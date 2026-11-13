package com.valxel.service.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.User;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ObservabilityTest {

    static Tracer tracer(String traceId, String spanId) {
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn(traceId);
        when(ctx.spanId()).thenReturn(spanId);
        return tracer;
    }

    static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";

    @Test
    void everyResponseNamesItsTrace() throws Exception {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("tracer", tracer(TRACE, "00f067aa0ba902b7"));
        var filter = new TraceResponseFilter(beans.getBeanProvider(Tracer.class));
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/greetings"), response, new MockFilterChain());
        assertThat(response.getHeader("traceresponse")).isEqualTo("00-" + TRACE + "-00f067aa0ba902b7-01");

        var none = new MockHttpServletResponse();
        new TraceResponseFilter(new DefaultListableBeanFactory().getBeanProvider(Tracer.class))
                .doFilter(new MockHttpServletRequest(), none, new MockFilterChain());
        assertThat(none.getHeader("traceresponse")).isNull();
    }

    @Test
    void anErrorReportKeepsTypeStackPathAndTraceButNothingAboutPeople() {
        SentryEvent event = new SentryEvent();
        User user = new User();
        user.setEmail("jane@example.com");
        event.setUser(user);
        event.setExtras(Map.of("form", "Jane"));
        event.setBreadcrumbs(List.of(new Breadcrumb("Saved Jane")));
        Request request = new Request();
        request.setMethod("POST");
        request.setUrl("https://svc/api/patients?name=Jane");
        request.setQueryString("name=Jane");
        request.setHeaders(Map.of("Authorization", "Bearer t"));
        request.setData("{\"name\":\"Jane\"}");
        event.setRequest(request);
        SentryException ex = new SentryException();
        ex.setType("DataIntegrityViolationException");
        ex.setValue("Key (name)=(Jane) already exists");
        event.setExceptions(List.of(ex));
        Message message = new Message();
        message.setMessage("Couldn't save greeting for {}");
        message.setFormatted("Couldn't save greeting for Jane");
        message.setParams(List.of("Jane"));
        event.setMessage(message);

        SentryEvent out = new ErrorReports(() -> tracer(TRACE, "00f067aa0ba902b7")).execute(event, new Hint());

        assertThat(out.getUser()).isNull();
        assertThat(out.getExtras()).isNullOrEmpty();
        assertThat(out.getBreadcrumbs()).isNullOrEmpty();
        assertThat(out.getRequest().getMethod()).isEqualTo("POST");
        assertThat(out.getRequest().getUrl()).isEqualTo("https://svc/api/patients");
        assertThat(out.getRequest().getQueryString()).isNull();
        assertThat(out.getRequest().getHeaders()).isNull();
        assertThat(out.getRequest().getData()).isNull();
        assertThat(out.getExceptions().get(0).getType()).isEqualTo("DataIntegrityViolationException");
        assertThat(out.getExceptions().get(0).getValue()).isNull();
        assertThat(out.getMessage().getMessage()).isEqualTo("Couldn't save greeting for {}");
        assertThat(out.getMessage().getFormatted()).isNull();
        assertThat(out.getMessage().getParams()).isNull();
        assertThat(out.getTag("trace_id")).isEqualTo(TRACE);
    }

    @Test
    void pathsLoseQueriesAndFragments() {
        assertThat(ErrorReports.pathOnly("/a?b#c")).isEqualTo("/a");
        assertThat(ErrorReports.pathOnly("/a#c?b")).isEqualTo("/a");
        assertThat(ErrorReports.pathOnly("/a")).isEqualTo("/a");
        assertThat(ErrorReports.pathOnly(null)).isNull();
        SentryEvent bare = new ErrorReports(() -> null).execute(new SentryEvent(), new Hint());
        assertThat(bare.getTag("trace_id")).isNull();
    }
}
