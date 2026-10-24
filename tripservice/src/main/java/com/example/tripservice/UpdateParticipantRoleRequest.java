package com.example.tripservice;

import jakarta.validation.constraints.NotNull;

public record UpdateParticipantRoleRequest(@NotNull TripRole role) {}
