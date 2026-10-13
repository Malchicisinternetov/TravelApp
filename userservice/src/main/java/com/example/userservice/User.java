package com.example.userservice;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Currency;

public record User(
        @Nullable
        Long id,
        ZoneId timezone,
        Currency currency,
        BigDecimal amount,
        @Nonnull           
        String name) {
}
