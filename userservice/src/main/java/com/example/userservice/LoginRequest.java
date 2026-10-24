package com.example.userservice;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9_.-]{3,50}") String username,
        @NotBlank @Size(max = 72) String password
) {}
