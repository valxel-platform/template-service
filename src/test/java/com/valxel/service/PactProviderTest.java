package com.valxel.service;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import com.valxel.service.security.AccessPolicy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Verifies every consumer's pact against the running application. Pacts are read from
 * src/test/resources/pacts; switch to @PactBroker once the product runs a Pact Broker, so
 * consumers publish and this build verifies the latest versions automatically.
 * Rename the provider to match this repository when you create it from the template.
 */
@Tag("contract")
@Provider("template-service")
@PactFolder("pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.enabled=false") // contracts cover the API's shape, not authentication
class PactProviderTest {

    @Value("${local.server.port}")
    int port;

    @BeforeEach
    void target(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    /**
     * Contracts cover the API's shape; who may change which record is the access tests' job
     * (SecurityTest). Without a signed-in caller there is no tenant, so allow every record here.
     */
    @TestConfiguration
    static class AnyRecord {
        @Bean
        @Primary
        AccessPolicy anyRecord() {
            return (caller, resource, action) -> {};
        }
    }

    @State("greetings are available")
    void greetingsAreAvailable() {
        // Set up data for the interaction here (the template needs none).
    }
}
