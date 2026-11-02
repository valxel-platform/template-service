package com.vrixel.service.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetsByName() {
        assertThat(service.greet("Ada")).isEqualTo(new Greeting("Hello, Ada!"));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(service.greet("  Ada  ").message()).isEqualTo("Hello, Ada!");
    }

    @Test
    void rejectsNamesThatAreTooLong() {
        assertThatThrownBy(() -> service.greet("x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must be at most 100 characters");
    }

    @Test
    void rejectsBlankNames() {
        assertThatThrownBy(() -> service.greet("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("name must not be blank");
    }
}
