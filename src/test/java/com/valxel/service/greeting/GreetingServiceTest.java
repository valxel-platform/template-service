package com.valxel.service.greeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.valxel.service.flags.Flags;
import dev.openfeature.sdk.NoOpProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import java.util.Map;
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

    @Test
    void aCommandIsCheckedBeforeItsEventIsPublished() {
        assertThat(service.validate(new SaveGreeting(" Ada ", "  Hi  "))).isEqualTo("Hi");
        assertThatThrownBy(() -> service.validate(new SaveGreeting("Ada", " ")))
                .hasMessage("message must not be blank");
        assertThatThrownBy(() -> service.validate(new SaveGreeting("Ada", "x".repeat(201))))
                .hasMessage("message must be at most 200 characters");
        assertThatThrownBy(() -> service.validate(new SaveGreeting(" ", "Hi")))
                .hasMessage("name must not be blank");
    }

    /** The pattern every feature follows: its new behaviour reads its flag, and off is today's. */
    @Test
    void theExampleFeatureIsBehindItsFlag() {
        assertThat(GreetingService.exampleFlag("emr")).isEqualTo("emr.example.greeting-welcome-back");
        assertThat(GreetingService.exampleFlag("")).isEqualTo("example.example.greeting-welcome-back");
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        try {
            api.setProviderAndWait(new InMemoryProvider(Map.of()));
            GreetingService off = new GreetingService(new Flags(api.getClient()), "emr");
            assertThat(off.greet("Ada", "user-1").message()).isEqualTo("Hello, Ada!");
            Flag<Boolean> on = Flag.<Boolean>builder()
                    .variant("on", true)
                    .variant("off", false)
                    .defaultVariant("on")
                    .build();
            api.setProviderAndWait(new InMemoryProvider(Map.of("emr.example.greeting-welcome-back", on)));
            GreetingService withFlag = new GreetingService(new Flags(api.getClient()), "emr");
            assertThat(withFlag.greet("Ada", "user-1").message()).isEqualTo("Hello, Ada! Welcome back.");
            assertThat(service.greet("Ada", "user-1").message()).isEqualTo("Hello, Ada!");
        } finally {
            api.setProviderAndWait(new NoOpProvider());
        }
    }
}
