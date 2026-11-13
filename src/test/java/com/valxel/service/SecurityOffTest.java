package com.valxel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * app.security.enabled=false (contract tests only) replaces the API chain rather than adding a
 * second one, so the switch can never leave both chains active.
 */
@SpringBootTest(properties = "app.security.enabled=false")
class SecurityOffTest {

    @Autowired
    WebApplicationContext context;

    @Test
    void replacesTheApiChain() throws Exception {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("open");
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/api/v1/greetings").param("name", "Ada"))
                .andExpect(status().isOk());
    }
}
