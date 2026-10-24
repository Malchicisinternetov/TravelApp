package com.example.tripservice;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddParticipantRequest(@NotNull @Positive Long userId, @NotNull TripRole role) {}
