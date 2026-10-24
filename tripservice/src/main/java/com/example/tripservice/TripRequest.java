package com.example.tripservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TripRequest(
        @NotBlank 
        @Size(max = 255)
        String name,
        @Size(max = 4000) 
        String description,
        @NotEmpty 
        List<@NotNull @Valid TripLegRequest> legs
) {}
