package com.vrixel.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Anonymous calls are refused; health probes stay open; a valid token gets through. */
@SpringBootTest
class SecurityTest {

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void refusesAnonymousApiCalls() throws Exception {
        mvc.perform(get("/api/v1/greetings").param("name", "Ada")).andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsAValidToken() throws Exception {
        mvc.perform(get("/api/v1/greetings").param("name", "Ada").with(jwt())).andExpect(status().isOk());
    }

    @Test
    void leavesHealthProbesOpen() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }
}
