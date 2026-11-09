package com.vrixel.service.flags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import jakarta.servlet.ServletException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FlagCohortsTest {

    private static final String KEY = "emr.emr-feat-3.check-in-kiosk";
    private final OpenFeatureAPI api = OpenFeatureAPI.getInstance();

    @AfterEach
    void noFlags() {
        api.setProviderAndWait(new NoOpProvider());
    }

    private Flags flags(boolean on) {
        Flag<Boolean> flag = Flag.<Boolean>builder().variant("on", true).variant("off", false)
                .defaultVariant(on ? "on" : "off").build();
        api.setProviderAndWait(new InMemoryProvider(Map.of(KEY, flag)));
        return new Flags(api.getClient());
    }

    @Test
    void eachRequestCountsOnceOnTheSideTheCallerGot() throws Exception {
        FlagCohorts cohorts = new FlagCohorts();
        FlagCohortFilter filter = new FlagCohortFilter(cohorts);
        Flags on = flags(true);

        MockHttpServletResponse ok = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), ok, (req, res) -> {
            on.enabled(KEY, "nurse-1");
            on.enabled(KEY, "nurse-1"); // twice in one request: still one request
        });
        MockHttpServletResponse failed = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), failed, (req, res) -> {
            on.enabled(KEY, "nurse-2");
            ((MockHttpServletResponse) res).setStatus(503);
        });
        Flags off = flags(false);
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> {
                    off.enabled(KEY, "nurse-3");
                    throw new ServletException("boom");
                })).isInstanceOf(ServletException.class);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> { });

        assertThat(cohorts.drain()).containsExactly(FlagCohorts.MARKER + " {\"flag\":\"" + KEY
                + "\",\"on\":{\"requests\":2,\"errors\":1,\"slow\":0},\"off\":{\"requests\":1,\"errors\":1,\"slow\":0}}");
        assertThat(cohorts.drain()).isEmpty(); // counts start again
    }

    @Test
    void theReportingCounterLogsWhatItHasWhenItStops() {
        FlagCohorts reporting = FlagCohorts.reporting();
        FlagCohorts.begin();
        FlagCohorts.seen(KEY, true);
        FlagCohorts.seen("not a flag key!", true);
        reporting.end(false);
        reporting.close();
        assertThat(reporting.drain()).isEmpty(); // reported (logged) on close
    }

    @Test
    void outsideARequestNothingIsCounted() {
        FlagCohorts cohorts = new FlagCohorts();
        flags(true).enabled(KEY, "batch");
        cohorts.end(false);
        assertThat(cohorts.drain()).isEmpty();
    }

    @Test
    void aSlowRequestCountsAsSlowOnItsSide() {
        FlagCohorts cohorts = new FlagCohorts();
        FlagCohorts.begin();
        FlagCohorts.seen(KEY, true);
        cohorts.end(false, FlagCohorts.SLOW_MS + 1);
        FlagCohorts.begin();
        FlagCohorts.seen(KEY, false);
        cohorts.end(false, 1);
        assertThat(cohorts.drain()).containsExactly(FlagCohorts.MARKER + " {\"flag\":\"" + KEY
                + "\",\"on\":{\"requests\":1,\"errors\":0,\"slow\":1},\"off\":{\"requests\":1,\"errors\":0,\"slow\":0}}");
        assertThat(FlagCohorts.slowMillis("250")).isEqualTo(250);
        assertThat(FlagCohorts.slowMillis("nope")).isEqualTo(FlagCohorts.slowMillis(null));
    }
}
