package com.valxel.service.greeting;

/** Response body of GET /api/v1/greetings. Records keep API types immutable. */
public record Greeting(String message) {}
