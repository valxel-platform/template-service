package com.valxel.service.greeting;

import com.valxel.service.config.InvalidRequestException;
import com.valxel.service.flags.Flags;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Business logic lives in services, free of HTTP concerns, so it is cheap to unit test. */
@Service
public class GreetingService {

    static final int MAX_NAME = 100;
    static final int MAX_MESSAGE = 200;

    private final Flags flags;
    private final String product;

    /** No flags: every feature reads as off (unit tests). */
    public GreetingService() {
        this(null, "");
    }

    @Autowired
    public GreetingService(Flags flags, @Value("${app.audit.product:}") String product) {
        this.flags = flags;
        this.product = product == null ? "" : product;
    }

    /**
     * The example feature's flag. It shows the pattern every feature follows: the tool gives the
     * feature a flag in the product's Flipt (on in dev and staging, off in production until it's
     * released) and the new behaviour reads it. Replace it with your first feature's own.
     */
    public static String exampleFlag(String product) {
        return (product == null || product.isBlank() ? "example" : product) + ".example.greeting-welcome-back";
    }

    /** The greeting for this caller: the example feature, behind its flag, adds a welcome. */
    public Greeting greet(String name, String user) {
        Greeting greeting = greet(name);
        if (flags != null && flags.enabled(exampleFlag(product), user)) {
            return new Greeting(greeting.message() + " Welcome back.");
        }
        return greeting;
    }

    public Greeting greet(String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidRequestException("name must not be blank");
        }
        if (trimmed.length() > MAX_NAME) {
            throw new InvalidRequestException("name must be at most " + MAX_NAME + " characters");
        }
        return new Greeting("Hello, " + trimmed + "!");
    }

    /** The checks a command must pass before its event is published: the event is the change. */
    public String validate(SaveGreeting command) {
        greet(command.name());
        String message = command.message() == null ? "" : command.message().strip();
        if (message.isEmpty()) {
            throw new InvalidRequestException("message must not be blank");
        }
        if (message.length() > MAX_MESSAGE) {
            throw new InvalidRequestException("message must be at most " + MAX_MESSAGE + " characters");
        }
        return message;
    }
}
