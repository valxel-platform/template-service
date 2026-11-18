package com.valxel.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The audit events this service builds, written to build/audit-events/events.jsonl for CI to check
 * against the shared contract: contracts/audit/valxel.audit.v1.schema.json, a byte-identical copy
 * of ss-audit-sink's schema (.github/scripts/check_audit_events.py; the Valxel SDLC checks daily
 * that the copy is still the sink's). Update the copy from the sink; never edit it here.
 */
class AuditSchemaContractTest {

    @Test
    void theEventsThisServiceBuildsAreWrittenForTheSchemaCheck() throws IOException {
        RecordingSink sink = new RecordingSink();
        AuditProperties props = new AuditProperties("log", "", "", true, null, "acme", "test", "phi", Map.of());
        Auditor auditor = new Auditor(sink, props, "svc", Clock.systemUTC(),
                () -> new String[] {"0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331"});

        auditor.record("patient.read", new Actor("u-1", "user", "web"), "patient", List.of("p-2", "p-1"),
                "success", "", "", Map.of("count", 2));
        auditor.record("patient.update", new Actor("agent-1", "agent", ""), "patient", List.of("p-1"),
                "denied", "missing scope", "u-1", Map.of());
        auditor.record("token.issue", Actor.service("svc-2"), "", List.of());

        Path out = Path.of("build", "audit-events", "events.jsonl");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sink.events.stream().map(e -> e.jsonString()).collect(Collectors.joining("\n")) + "\n");
        assertThat(sink.events).hasSize(3);
        assertThat(Files.readString(Path.of("contracts", "audit", "valxel.audit.v1.schema.json")))
                .contains("\"const\": \"valxel.audit.v1\"");
    }
}
