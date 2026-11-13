package com.valxel.service.greeting;

import com.valxel.service.config.InvalidRequestException;
import org.springframework.stereotype.Service;

/** Business logic lives in services, free of HTTP concerns, so it is cheap to unit test. */
@Service
public class GreetingService {

    static final int MAX_NAME = 100;
    static final int MAX_MESSAGE = 200;

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
