package com.vrixel.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.DocumentContext;
import io.micrometer.tracing.Tracer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditorTest {

    @Test
    void aRecordNamesWhoDidWhatToWhichRecords() {
        RecordingSink sink = new RecordingSink();
        AuditProperties props = new AuditProperties("log", "", "", true, null, "emr", "staging", "phi", Map.of());
        Clock fixed = Clock.fixed(Instant.parse("2026-11-04T06:00:00Z"), ZoneOffset.UTC);
        Auditor auditor = new Auditor(sink, props, "intake-api", fixed, () -> new String[] {"ab", "cd"});

        auditor.record("patient.read", new Actor("svc-intake", "service", "intake"), "patient",
                Arrays.asList("p-2", "p-1", "p-1", "", null), "success", "", "nurse-7", Map.of("count", 2));

        DocumentContext e = sink.events.getFirst();
        assertThat(sink.keys).containsExactly("patient/p-1,p-2");
        assertThat(e.read("$.specversion", String.class)).isEqualTo("1.0");
        assertThat(e.read("$.type", String.class)).isEqualTo("vrixel.audit.v1");
        assertThat(e.read("$.source", String.class)).isEqualTo("emr/intake-api");
        assertThat(e.read("$.time", String.class)).isEqualTo("2026-11-04T06:00:00Z");
        assertThat(e.read("$.data.actor.id", String.class)).isEqualTo("svc-intake");
        assertThat(e.read("$.data.actor.type", String.class)).isEqualTo("service");
        assertThat(e.read("$.data.on_behalf_of", String.class)).isEqualTo("nurse-7");
        assertThat(e.read("$.data.resource.ids", List.class)).containsExactly("p-1", "p-2");
        assertThat(e.read("$.data.data_class", String.class)).isEqualTo("phi");
        assertThat(e.read("$.data.trace_id", String.class)).isEqualTo("ab");
        assertThat(e.read("$.data.details.count", Integer.class)).isEqualTo(2);
    }

    @Test
    void anActionWithoutARecordIsItsOwnSubject() {
        RecordingSink sink = new RecordingSink();
        RecordingSink.auditor(sink, true).record("login", Actor.user("u1"), "", List.of());
        assertThat(sink.keys).containsExactly("login");
        assertThat(sink.events.getFirst().read("$.source", String.class)).isEqualTo("emr/template-service");
    }

    @Test
    void aRecordThatCantBePublishedFailsTheAction() {
        RecordingSink sink = new RecordingSink();
        sink.fail = true;
        assertThatThrownBy(() -> RecordingSink.auditor(sink, true).record("patient.read", Actor.user("u"), "patient", List.of("p")))
                .isInstanceOf(AuditUnavailableException.class);
    }

    @Test
    void bestEffortRecordsAreDroppedWithAWarning() {
        RecordingSink sink = new RecordingSink();
        sink.fail = true;
        Map<String, Object> event = RecordingSink.auditor(sink, false)
                .record("patient.read", Actor.user("u"), "patient", List.of("p"));
        assertThat(event).containsKey("id");
    }

    @Test
    void jsonIsEscaped() {
        assertThat(Json.write(Map.of("k", "a\"b\\c\nd\te\r\u0001")))
                .isEqualTo("{\"k\":\"a\\\"b\\\\c\\nd\\te\\r\\u0001\"}");
        assertThat(Json.write(Arrays.asList(1, true, null, Instant.EPOCH))).isEqualTo("[1,true,null,\"1970-01-01T00:00:00Z\"]");
    }

    @Test
    void propertiesHaveSafeDefaults() {
        AuditProperties p = new AuditProperties(null, null, " ", null, null, null, null, null, null);
        assertThat(p.sink()).isEqualTo("log");
        assertThat(p.topic()).isEqualTo("audit.v1");
        assertThat(p.required()).isTrue();
        assertThat(p.timeoutMs()).isEqualTo(5000);
        assertThat(p.dataClass()).isEqualTo("general");
        assertThat(p.kafka()).isEmpty();
        assertThat(new Actor(null, null, null)).isEqualTo(new Actor("", "user", ""));
        assertThat(Actor.service("s").type()).isEqualTo("service");
    }

    @Test
    void sinksAreChosenByName() {
        assertThat(AuditConfig.sink(props("log", ""))).isInstanceOf(LogAuditSink.class);
        assertThatThrownBy(() -> AuditConfig.sink(props("kafka", ""))).hasMessageContaining("bootstrap");
        assertThatThrownBy(() -> AuditConfig.sink(props("syslog", ""))).hasMessageContaining("Unknown");
        try (KafkaAuditSink kafka = (KafkaAuditSink) AuditConfig.sink(props("kafka", "127.0.0.1:9"))) {
            assertThat(kafka).isNotNull();
        }
        new LogAuditSink().publish("k", "{}");
    }

    @Test
    void traceIdsComeFromTheCurrentSpan() {
        assertThat(AuditConfig.traceIds(null)).containsExactly("", "");
        assertThat(AuditConfig.traceIds(Tracer.NOOP)).hasSize(2);
    }

    private static AuditProperties props(String sink, String bootstrap) {
        return new AuditProperties(sink, bootstrap, "t", true, 200, "", "", "", Map.of());
    }
}
