package com.valxel.service.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PathsTest {

    @Test
    void segmentsWithDigitsAreMasked() {
        assertThat(Paths.withoutIds("/api/v1/patients/MRN-55821/notes")).isEqualTo("/api/{id}/patients/{id}/notes");
        assertThat(Paths.withoutIds("https://svc-1:8080/api/patients/p-102")).isEqualTo("https://svc-1:8080/api/patients/{id}");
        assertThat(Paths.withoutIds("https://svc/api/patients")).isEqualTo("https://svc/api/patients");
        assertThat(Paths.withoutIds("https://svc")).isEqualTo("https://svc");
        assertThat(Paths.withoutIds(null)).isNull();
    }
}
