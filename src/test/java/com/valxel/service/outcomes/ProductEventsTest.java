package com.valxel.service.outcomes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.valxel.service.flags.FlagCohortFilter;
import com.valxel.service.flags.FlagCohorts;
import com.valxel.service.flags.Flags;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProductEventsTest {

    private static final String KEY = "emr.emr-feat-3.check-in-kiosk";
    private final OpenFeatureAPI api = OpenFeatureAPI.getInstance();

    @AfterEach
    void reset() {
        api.setProviderAndWait(new NoOpProvider());
        ProductEvents.counting();
    }

    @Test
    void eventsAreCountedIntoOneSummaryLineAMinute() {
        ProductEvents events = ProductEvents.counting();
        ProductEvents.record("checkin_completed");
        ProductEvents.record("checkin_completed", Map.of("visit_type", "follow_up"));
        ProductEvents.record("kiosk.abandoned");
        // The SDLC tool reads exactly this: the marker, then a JSON object of counts.
        assertThat(events.drain()).containsExactly(
                ProductEvents.MARKER + " {\"checkin_completed\":2,\"kiosk.abandoned\":1}");
        assertThat(events.drain()).isEmpty(); // counts start again; nothing happened, no line
    }

    @Test
    void theCounterCarriesTheEventAndItsCategories() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try (ProductEvents ignored = ProductEvents.reporting(meters)) {
            ProductEvents.record("checkin_completed", Map.of("visit_type", "follow_up"));
            ProductEvents.record("checkin_completed", Map.of("visit_type", "follow_up"));
            ProductEvents.record("checkin_completed");
            assertThat(meters.get(ProductEvents.METER).tag("event", "checkin_completed").tag("visit_type", "follow_up")
                    .counter().count()).isEqualTo(2.0);
            assertThat(meters.get(ProductEvents.METER).tags("event", "checkin_completed").counters()
                    .stream().mapToDouble(c -> c.count()).sum()).isEqualTo(3.0);
        }
    }

    @Test
    void namesAreLowercaseWordsAndAttributesNeverReachAPerson() {
        assertThatThrownBy(() -> ProductEvents.record("CheckinCompleted")).hasMessageContaining("lowercase");
        assertThatThrownBy(() -> ProductEvents.record("checkin completed")).isInstanceOf(IllegalArgumentException.class);
        for (String attribute : new String[] {"email", "patient_id", "first_name", "user_id", "patient_email",
                "account_number", "patient_name"}) {
            assertThatThrownBy(() -> ProductEvents.record("checkin_completed", Map.of(attribute, "x")))
                    .as(attribute).hasMessageContaining("person");
        }
        ProductEvents.record("checkin_completed", Map.of("clinic_name", "north", "visit_type", "new", "plan", "basic"));
        assertThatThrownBy(() -> ProductEvents.record("checkin_completed", Map.of("event", "x")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProductEvents.record("checkin_completed", Map.of("note", "x".repeat(65))))
                .hasMessageContaining("short category");
    }

    private Flags flags(boolean on) {
        Flag<Boolean> flag = Flag.<Boolean>builder().variant("on", true).variant("off", false)
                .defaultVariant(on ? "on" : "off").build();
        api.setProviderAndWait(new InMemoryProvider(Map.of(KEY, flag)));
        return new Flags(api.getClient());
    }

    @Test
    void aRequestsEventsCountOnTheSideOfEachFlagItEvaluated() throws Exception {
        ProductEvents.counting();
        FlagCohorts cohorts = new FlagCohorts();
        FlagCohortFilter filter = new FlagCohortFilter(cohorts);
        Flags on = flags(true);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            on.enabled(KEY, "nurse-1");
            ProductEvents.record("checkin_completed");
            ProductEvents.record("checkin_completed");
        });
        Flags off = flags(false);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
            off.enabled(KEY, "nurse-2");
            ProductEvents.record("checkin_completed");
        });
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> off.enabled(KEY, "nurse-3"));
        assertThat(cohorts.drain()).containsExactly(FlagCohorts.MARKER + " {\"flag\":\"" + KEY
                + "\",\"on\":{\"requests\":1,\"errors\":0,\"slow\":0,\"events\":{\"checkin_completed\":2}},"
                + "\"off\":{\"requests\":2,\"errors\":0,\"slow\":0,\"events\":{\"checkin_completed\":1}}}");
        ProductEvents.record("checkin_completed"); // outside a request: the summary line counts it, no cohort does
        assertThat(cohorts.drain()).isEmpty();
    }
}
