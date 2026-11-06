package com.vrixel.service;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The permission matrix of the example API: no token 401; a token without the endpoint's scope
 * 403; the right scope but no tenant 403 on a write; the right scope and tenant gets through.
 */
@SpringBootTest
class SecurityTest {

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    WebApplicationContext context;

    private MockMvc mvc;

    private static final String BODY = "{\"name\":\"Ada\",\"message\":\"Hi\"}";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void refusesAnonymousApiCalls() throws Exception {
        mvc.perform(get("/api/v1/greetings").param("name", "Ada")).andExpect(status().isUnauthorized());
    }

    @Test
    void needsTheEndpointsPermission() throws Exception {
        mvc.perform(get("/api/v1/greetings").param("name", "Ada").with(jwt()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/greetings").param("name", "Ada")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_greetings:read"))))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_greetings:read"))))
                .andExpect(status().isForbidden()); // read is not write
    }

    @Test
    void aWriteNeedsTheCallersTenant() throws Exception {
        var write = new SimpleGrantedAuthority("SCOPE_greetings:write");
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwt().authorities(write)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/greetings/g-1").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(jwt().jwt(j -> j.claim("organization", "clinic-1")).authorities(write)))
                .andExpect(status().isAccepted());
    }

    @Test
    void anythingOutsideTheApiIsDenied() throws Exception {
        mvc.perform(get("/internal/debug").with(jwt())).andExpect(status().isForbidden());
    }
}
