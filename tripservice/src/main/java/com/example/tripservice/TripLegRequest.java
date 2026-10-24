package com.example.tripservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record TripLegRequest(
        @NotBlank 
        @Size(max = 255) 
        String departurePoint,
        @NotBlank 
        @Size(max = 255) 
        String destinationPoint,
        @NotNull 
        LocalDateTime departureTime,
        @NotNull 
        LocalDateTime arrivalTime,
        @NotBlank 
        @Size(max = 255) 
        String departureZone,
        @NotBlank 
        @Size(max = 255) 
        String arrivalZone
) {}
