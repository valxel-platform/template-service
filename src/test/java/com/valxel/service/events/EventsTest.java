package com.valxel.service.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventsTest {

    private final RecordingEventSink sink = new RecordingEventSink();
    private final Events events = RecordingEventSink.events(sink);

    @Test
    void anEventIsACloudEventOnTheEntitysTopicKeyedByTheRecord() {
        Events.Published p = events.publish("patients", "registered", "p-1", Map.of("mrn", "123"), null);

        RecordingEventSink.Sent sent = sink.sent.getFirst();
        assertThat(sent.topic()).isEqualTo("emr.intake-api.patients.v1");
        assertThat(sent.key()).isEqualTo("p-1"); // every change to p-1 lands on one partition, in order
        assertThat(sent.event().read("$.specversion", String.class)).isEqualTo("1.0");
        assertThat(sent.event().read("$.type", String.class)).isEqualTo("valxel.patient.registered.v1");
        assertThat(sent.event().read("$.subject", String.class)).isEqualTo("patient/p-1");
        assertThat(sent.event().read("$.source", String.class)).isEqualTo("emr/intake-api");
        assertThat(sent.event().read("$.data.mrn", String.class)).isEqualTo("123");
        assertThat(sent.event().read("$.id", String.class)).isEqualTo(p.eventId());
        assertThat(sent.headers()).containsEntry("ce_type", "valxel.patient.registered.v1")
                .containsEntry("ce_id", p.eventId())
                .containsEntry("traceparent", "00-" + "t".repeat(32) + "-" + "s".repeat(16) + "-01");
        assertThat(p.position().offset()).isZero();
    }

    @Test
    void aRetryWithTheSameIdempotencyKeyIsTheSameEvent() {
        String first = events.publish("patients", "registered", "p-1", Map.of(), "key-1").eventId();
        String again = events.publish("patients", "registered", "p-1", Map.of(), "key-1").eventId();
        String other = events.publish("patients", "registered", "p-1", Map.of(), "key-2").eventId();
        String unkeyed = events.publish("patients", "registered", "p-1", Map.of(), null).eventId();
        assertThat(again).isEqualTo(first);
        assertThat(other).isNotEqualTo(first);
        assertThat(unkeyed).isNotEqualTo(first);
    }

    @Test
    void noTraceNoTraceparent() {
        EventsProperties props = new EventsProperties(null, null, null, null, null, null);
        Events untraced = new Events(sink, props, "svc", Clock.systemUTC(), () -> new String[] {"", ""});
        untraced.publish("orders", "placed", "o-1", Map.of(), null);
        assertThat(sink.sent.getFirst().headers()).doesNotContainKey("traceparent");
        assertThat(sink.sent.getFirst().topic()).isEqualTo("local.orders.v1");
        assertThat(sink.sent.getFirst().event().read("$.source", String.class)).isEqualTo("svc");
    }

    @Test
    void anUnsampledTraceStaysUnsampled() {
        EventsProperties props = new EventsProperties(null, null, null, null, null, null);
        Events unsampled = new Events(sink, props, "svc", Clock.systemUTC(),
                () -> new String[] {"a".repeat(32), "b".repeat(16), "0"});
        unsampled.publish("orders", "placed", "o-1", Map.of(), null);
        assertThat(sink.sent.getFirst().headers()).containsEntry("traceparent", "00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-00");
    }

    @Test
    void phiEventsNeverTravelInPlainText() {
        assertThatThrownBy(() -> EventsConfig.sink(
                        new EventsProperties("kafka", "127.0.0.1:9", null, null, null, null), "svc", "phi"))
                .hasMessageContaining("must use TLS");
    }

    @Test
    void aChangeKafkaDoesntConfirmDidNotHappen() {
        sink.fail = true;
        assertThatThrownBy(() -> events.publish("patients", "registered", "p-1", Map.of(), null))
                .isInstanceOf(EventsUnavailableException.class);
    }

    @Test
    void anEventNeedsTheRecordsId() {
        assertThatThrownBy(() -> events.publish("patients", "registered", " ", Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSinkIsChosenBySettings() {
        assertThat(EventsConfig.sink(new EventsProperties("log", null, null, null, null, null), "svc"))
                .isInstanceOf(LogEventSink.class);
        assertThatThrownBy(() -> EventsConfig.sink(new EventsProperties("kafka", "", null, null, null, null), "svc"))
                .hasMessageContaining("EVENTS_KAFKA_BOOTSTRAP");
        assertThatThrownBy(() -> EventsConfig.sink(new EventsProperties("db", null, null, null, null, null), "svc"))
                .hasMessageContaining("use kafka or log");
    }
}
