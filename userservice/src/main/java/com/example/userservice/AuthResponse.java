package com.example.userservice;

public record AuthResponse(String accessToken, String tokenType, long expiresIn, Long userId) {}
