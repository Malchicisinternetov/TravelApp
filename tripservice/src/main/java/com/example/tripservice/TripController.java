package com.example.tripservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/trips")
public class TripController {
    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TripResponse> create(
            @RequestHeader("X-User-Id") @Positive Long actorId, @Valid @RequestBody TripRequest request) {
        TripResponse trip = service.create(actorId, request);
        return ResponseEntity.created(URI.create("/trips/" + trip.id())).body(trip);
    }

    @GetMapping
    public List<TripResponse> list(@RequestHeader("X-User-Id") @Positive Long actorId) {
        return service.list(actorId);
    }

    @GetMapping("/{tripId}")
    public TripResponse get(@PathVariable @Positive Long tripId,
                            @RequestHeader("X-User-Id") @Positive Long actorId) {
        return service.get(tripId, actorId);
    }

    @PutMapping("/{tripId}")
    public TripResponse update(@PathVariable @Positive Long tripId,
                               @RequestHeader("X-User-Id") @Positive Long actorId,
                               @Valid @RequestBody TripRequest request) {
        return service.update(tripId, actorId, request);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long tripId,
                                       @RequestHeader("X-User-Id") @Positive Long actorId) {
        service.delete(tripId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tripId}/participants")
    public List<TripResponse.ParticipantResponse> participants(
            @PathVariable @Positive Long tripId, @RequestHeader("X-User-Id") @Positive Long actorId) {
        return service.get(tripId, actorId).participants();
    }

    @PostMapping("/{tripId}/participants")
    public ResponseEntity<TripResponse> addParticipant(
            @PathVariable @Positive Long tripId, @RequestHeader("X-User-Id") @Positive Long actorId,
            @Valid @RequestBody AddParticipantRequest request) {
        return ResponseEntity.status(201).body(service.addParticipant(tripId, actorId, request));
    }

    @PutMapping("/{tripId}/participants/{userId}/role")
    public TripResponse updateRole(
            @PathVariable @Positive Long tripId, @PathVariable @Positive Long userId,
            @RequestHeader("X-User-Id") @Positive Long actorId,
            @Valid @RequestBody UpdateParticipantRoleRequest request) {
        return service.updateRole(tripId, actorId, userId, request.role());
    }

    @DeleteMapping("/{tripId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable @Positive Long tripId, @PathVariable @Positive Long userId,
            @RequestHeader("X-User-Id") @Positive Long actorId) {
        service.removeParticipant(tripId, actorId, userId);
        return ResponseEntity.noContent().build();
    }
}
