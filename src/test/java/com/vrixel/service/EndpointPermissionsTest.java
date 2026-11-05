package com.vrixel.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Deny by default (VX-17): every endpoint of this service declares the permission it needs. An
 * endpoint added without @PreAuthorize fails the build here, before anyone can call it.
 */
@SpringBootTest
class EndpointPermissionsTest {

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Test
    void everyEndpointDeclaresItsPermission() {
        List<HandlerMethod> ours = mappings.getHandlerMethods().values().stream()
                .filter(h -> h.getBeanType().getPackageName().startsWith("com.vrixel.service"))
                .toList();
        assertThat(ours).isNotEmpty();
        List<String> missing = ours.stream()
                .filter(h -> !AnnotatedElementUtils.hasAnnotation(h.getMethod(), PreAuthorize.class)
                        && !AnnotatedElementUtils.hasAnnotation(h.getBeanType(), PreAuthorize.class))
                .map(h -> h.getBeanType().getSimpleName() + "." + h.getMethod().getName())
                .toList();
        assertThat(missing).as("endpoints without @PreAuthorize").isEmpty();
    }
}
