package com.example.tripservice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class TripLeg {
    @Column(nullable = false)
    private String departurePoint;
    @Column(nullable = false)
    private String destinationPoint;
    @Column(nullable = false)
    private LocalDateTime departureTime;
    @Column(nullable = false)
    private LocalDateTime arrivalTime;
    @Column(nullable = false)
    private String departureZone;
    @Column(nullable = false)
    private String arrivalZone;

    public TripLeg(TripLegRequest request) {
        departurePoint = request.departurePoint();
        destinationPoint = request.destinationPoint();
        departureTime = request.departureTime();
        arrivalTime = request.arrivalTime();
        departureZone = request.departureZone();
        arrivalZone = request.arrivalZone();
    }
}
