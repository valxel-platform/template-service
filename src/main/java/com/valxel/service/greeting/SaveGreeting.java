package com.valxel.service.greeting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body of PUT /api/v1/greetings/{id}: a custom greeting for someone. */
public record SaveGreeting(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 200) String message) {}
