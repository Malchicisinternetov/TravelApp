package com.example.tripservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.security.Principal;
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
            Principal principal, @Valid @RequestBody TripRequest request) {
        TripResponse trip = service.create(userId(principal), request);
        return ResponseEntity.created(URI.create("/trips/" + trip.id())).body(trip);
    }

    @GetMapping
    public List<TripResponse> list(Principal principal) {
        return service.list(userId(principal));
    }

    @GetMapping("/{tripId}")
    public TripResponse get(@PathVariable @Positive Long tripId,
                            Principal principal) {
        return service.get(tripId, userId(principal));
    }

    @PutMapping("/{tripId}")
    public TripResponse update(@PathVariable @Positive Long tripId,
                               Principal principal,
                               @Valid @RequestBody TripRequest request) {
        return service.update(tripId, userId(principal), request);
    }

    @DeleteMapping("/{tripId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long tripId,
                                       Principal principal) {
        service.delete(tripId, userId(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tripId}/participants")
    public List<TripResponse.ParticipantResponse> participants(
            @PathVariable @Positive Long tripId, Principal principal) {
        return service.get(tripId, userId(principal)).participants();
    }

    @PostMapping("/{tripId}/participants")
    public ResponseEntity<TripResponse> addParticipant(
            @PathVariable @Positive Long tripId, Principal principal,
            @Valid @RequestBody AddParticipantRequest request) {
        return ResponseEntity.status(201).body(service.addParticipant(tripId, userId(principal), request));
    }

    @PutMapping("/{tripId}/participants/{userId}/role")
    public TripResponse updateRole(
            @PathVariable @Positive Long tripId, @PathVariable @Positive Long userId,
            Principal principal,
            @Valid @RequestBody UpdateParticipantRoleRequest request) {
        return service.updateRole(tripId, userId(principal), userId, request.role());
    }

    @DeleteMapping("/{tripId}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable @Positive Long tripId, @PathVariable @Positive Long userId,
            Principal principal) {
        service.removeParticipant(tripId, userId(principal), userId);
        return ResponseEntity.noContent().build();
    }

    private Long userId(Principal principal) {
        return Long.valueOf(principal.getName());
    }
}
