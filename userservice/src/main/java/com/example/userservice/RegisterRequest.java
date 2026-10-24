package com.example.userservice;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 255) String name
) {}
