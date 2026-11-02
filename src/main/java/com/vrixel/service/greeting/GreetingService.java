package com.vrixel.service.greeting;

import org.springframework.stereotype.Service;

/** Business logic lives in services, free of HTTP concerns, so it is cheap to unit test. */
@Service
public class GreetingService {

    static final int MAX_NAME = 100;

    public Greeting greet(String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (trimmed.length() > MAX_NAME) {
            throw new IllegalArgumentException("name must be at most " + MAX_NAME + " characters");
        }
        return new Greeting("Hello, " + trimmed + "!");
    }
}
