package com.valxel.service.outcomes;

import static org.assertj.core.api.Assertions.assertThat;

import com.valxel.service.flags.FlagCohorts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The summary lines this service logs, written to build/contract-lines/lines.txt for CI to check
 * against the shared contracts the SDLC tool reads them by: contracts/events/, byte-identical copies
 * of the platform's (.github/scripts/check_contract_lines.py; the Valxel SDLC keeps them in step,
 * so update them from there, never here).
 */
class SharedContractLinesTest {

    private static final String KEY = "acme.acme-feat-1.triage";

    @AfterEach
    void reset() {
        ProductEvents.counting();
    }

    private static void request(FlagCohorts cohorts, boolean on) {
        FlagCohorts.begin();
        FlagCohorts.seen(KEY, on);
        ProductEvents.record("referral_triaged");
        cohorts.end(!on, 10);
    }

    @Test
    void theEventAndCohortLinesAreWrittenForTheContractCheck() throws IOException {
        ProductEvents events = ProductEvents.counting();
        FlagCohorts cohorts = new FlagCohorts();
        request(cohorts, true);
        request(cohorts, false);
        List<String> lines = new ArrayList<>(events.drain());
        lines.addAll(cohorts.drain());

        Path out = Path.of("build", "contract-lines", "lines.txt");
        Files.createDirectories(out.getParent());
        Files.writeString(out, String.join("\n", lines) + "\n");
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("\"events\":{\"referral_triaged\":1}");
    }
}
