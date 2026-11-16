package com.valxel.service.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Events are checked against their topic's approved data contract before they reach Kafka. Synthetic data. */
class EventContractsTest {

    private final RecordingEventSink sink = new RecordingEventSink();
    private final List<String> counts = new ArrayList<>();
    private final EventContracts contracts = EventContracts.load(getClass().getClassLoader(), "contracts");
    private final Events events = new Events(sink,
            new EventsProperties(null, null, "emr.intake-api", "emr/intake-api", null, null), "template-service",
            Clock.systemUTC(), () -> new String[] {"", ""}, contracts, (d, o) -> counts.add(d + ":" + o));

    private static Map<String, Object> patient() {
        Map<String, Object> m = new HashMap<>();
        m.put("patient_id", "7d0f7c9e-5b3a-4f7e-9a51-0c1d2e3f4a5b");
        m.put("mrn", "1234");
        return m;
    }

    @Test
    void anEventThatFollowsItsContractCarriesTheContractVersion() {
        Map<String, Object> data = patient();
        data.putAll(Map.of("status", "active", "born_on", "1970-01-01", "visits", 3, "score", 1.5, "consented", true,
                "updated_at", "2026-11-16T10:00:00Z", "tags", List.of("x"), "address", Map.of("city", "Plano")));
        events.publish("patients", "registered", "p-1", data, null);
        assertThat(sink.sent.getFirst().event().read("$.dataschema", String.class))
                .isEqualTo("valxel:dataset/emr.patients/1.2.0");
        assertThat(counts).containsExactly("emr.patients:published");
        // A date may also be a java.time value (the writer prints it as text).
        Map<String, Object> temporal = patient();
        temporal.put("born_on", LocalDate.of(1970, 1, 1));
        EventContracts.check(contracts.forTopic("emr.intake-api.patients.v1").orElseThrow(), temporal);
    }

    @Test
    void anEventThatBreaksItsContractIsNeverPublished() {
        Map<String, Object> extra = patient();
        extra.put("ssn", "000-00-0000");
        assertThatThrownBy(() -> events.publish("patients", "registered", "p-1", extra, null))
                .isInstanceOf(EventContracts.ContractViolationException.class)
                .hasMessage("emr.patients: ssn breaks the data contract (not in the contract)")
                .hasMessageNotContaining("000-00-0000");
        assertThat(sink.sent).isEmpty();
        assertThat(counts).containsExactly("emr.patients:refused");
    }

    @Test
    void eachRuleIsChecked() {
        String[][] cases = {
            {"patient_id", null, "required"}, {"patient_id", "not-a-uuid", "format uuid"}, {"mrn", "12a", "pattern"},
            {"mrn", "1234567890123", "maxLength"}, {"status", "deleted", "enum"}, {"born_on", "01/01/1970", "format date"},
            {"updated_at", "yesterday", "format date-time"}, {"visits", "3", "type integer"}};
        for (String[] c : cases) {
            Map<String, Object> data = patient();
            data.put(c[0], c[1]);
            assertThatThrownBy(() -> events.publish("patients", "registered", "p-1", data, null))
                    .as(c[0])
                    .isInstanceOfSatisfying(EventContracts.ContractViolationException.class,
                            e -> assertThat(List.of(e.dataset(), e.field(), e.rule())).containsExactly("emr.patients", c[0], c[2]));
        }
        Map<String, Object> wrong = patient();
        wrong.putAll(Map.of("score", "high"));
        assertThatThrownBy(() -> events.publish("patients", "registered", "p-1", wrong, null)).hasMessageContaining("type number");
        Map<String, Object> notBool = patient();
        notBool.putAll(Map.of("consented", "yes", "tags", "x", "address", "x"));
        assertThatThrownBy(() -> events.publish("patients", "registered", "p-1", notBool, null))
                .isInstanceOf(EventContracts.ContractViolationException.class);
    }

    @Test
    void aTopicWithoutAContractAndAServiceWithoutContractsPublishAsBefore() {
        events.publish("visits", "opened", "v-1", Map.of("anything", 1), null);
        assertThat(sink.sent.getFirst().event().read("$.data.anything", Integer.class)).isEqualTo(1);
        assertThat(counts).containsExactly("emr.intake-api.visits.v1:published");
        assertThat(EventContracts.load(getClass().getClassLoader(), "no-such-dir").forTopic("emr.intake-api.patients.v1")).isEmpty();
        assertThatThrownBy(() -> EventContracts.load(getClass().getClassLoader(), "broken-contracts"))
                .hasMessageContaining("emr.ghost.json is missing");
    }
}
