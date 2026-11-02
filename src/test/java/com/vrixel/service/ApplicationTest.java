package com.vrixel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The application starts with its production configuration (security on). */
@SpringBootTest
class ApplicationTest {

    @MockitoBean
    JwtDecoder jwtDecoder; // no identity provider in unit tests

    @Autowired
    ApplicationContext context;

    @Test
    void startsWithSecurityOn() {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("api");
    }
}
