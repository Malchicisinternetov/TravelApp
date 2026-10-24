package com.example.tripservice;

import java.util.List;

public record TripResponse(Long id, String name, String description,
                           List<ParticipantResponse> participants, List<TripLegRequest> legs) {
    public record ParticipantResponse(Long userId, TripRole role) {}

    public static TripResponse from(TripEntity trip) {
        return new TripResponse(trip.getId(), trip.getName(), trip.getDescription(),
                trip.getParticipants().stream()
                        .map(p -> new ParticipantResponse(p.getUserId(), p.getRole())).toList(),
                trip.getLegs().stream().map(l -> new TripLegRequest(l.getDeparturePoint(),
                        l.getDestinationPoint(), l.getDepartureTime(), l.getArrivalTime(),
                        l.getDepartureZone(), l.getArrivalZone())).toList());
    }
}
