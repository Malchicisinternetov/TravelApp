package com.example.tripservice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class TripService {
    private final TripRepository repository;

    public TripService(TripRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TripResponse create(Long actorId, TripRequest request) {
        validateRoute(request.legs());
        TripEntity trip = new TripEntity();
        applyDetails(trip, request);
        trip.getParticipants().add(new TripParticipant(actorId, TripRole.OWNER));
        return TripResponse.from(repository.save(trip));
    }

    public List<TripResponse> list(Long actorId) {
        return repository.findAllForUser(actorId).stream().map(TripResponse::from).toList();
    }

    public TripResponse get(Long tripId, Long actorId) {
        TripEntity trip = find(tripId, false);
        requireParticipant(trip, actorId);
        return TripResponse.from(trip);
    }

    @Transactional
    public TripResponse update(Long tripId, Long actorId, TripRequest request) {
        TripEntity trip = find(tripId, true);
        if (requireParticipant(trip, actorId).getRole() == TripRole.READ_ONLY) {
            throw new TripException(FORBIDDEN, "Read-only participants cannot edit trips");
        }
        validateRoute(request.legs());
        applyDetails(trip, request);
        return TripResponse.from(trip);
    }

    @Transactional
    public void delete(Long tripId, Long actorId) {
        TripEntity trip = find(tripId, true);
        requireOwner(trip, actorId);
        repository.delete(trip);
    }

    @Transactional
    public TripResponse addParticipant(Long tripId, Long actorId, AddParticipantRequest request) {
        TripEntity trip = find(tripId, true);
        requireOwner(trip, actorId);
        if (trip.getParticipants().stream().anyMatch(p -> p.getUserId().equals(request.userId()))) {
            throw new TripException(CONFLICT, "User is already a trip participant");
        }
        trip.getParticipants().add(new TripParticipant(request.userId(), request.role()));
        return TripResponse.from(trip);
    }

    @Transactional
    public TripResponse updateRole(Long tripId, Long actorId, Long userId, TripRole role) {
        TripEntity trip = find(tripId, true);
        requireOwner(trip, actorId);
        TripParticipant participant = findParticipant(trip, userId);
        if (role != TripRole.OWNER) {
            protectLastOwner(trip, participant);
        }
        participant.setRole(role);
        return TripResponse.from(trip);
    }

    @Transactional
    public void removeParticipant(Long tripId, Long actorId, Long userId) {
        TripEntity trip = find(tripId, true);
        requireOwner(trip, actorId);
        TripParticipant participant = findParticipant(trip, userId);
        protectLastOwner(trip, participant);
        trip.getParticipants().remove(participant);
    }

    private TripEntity find(Long id, boolean forUpdate) {
        return (forUpdate ? repository.findForUpdate(id) : repository.findById(id))
                .orElseThrow(() -> new TripException(NOT_FOUND, "Trip not found: " + id));
    }

    private TripParticipant requireParticipant(TripEntity trip, Long userId) {
        return trip.getParticipants().stream().filter(p -> p.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new TripException(FORBIDDEN, "User is not a trip participant"));
    }

    private TripParticipant findParticipant(TripEntity trip, Long userId) {
        return trip.getParticipants().stream().filter(p -> p.getUserId().equals(userId)).findFirst()
                .orElseThrow(() -> new TripException(NOT_FOUND, "Participant not found: " + userId));
    }

    private void requireOwner(TripEntity trip, Long userId) {
        if (requireParticipant(trip, userId).getRole() != TripRole.OWNER) {
            throw new TripException(FORBIDDEN, "Only owners can manage participants or delete trips");
        }
    }

    private void protectLastOwner(TripEntity trip, TripParticipant participant) {
        if (participant.getRole() == TripRole.OWNER &&
                trip.getParticipants().stream().filter(p -> p.getRole() == TripRole.OWNER).count() == 1) {
            throw new TripException(CONFLICT, "A trip must have at least one owner");
        }
    }

    private void applyDetails(TripEntity trip, TripRequest request) {
        trip.setName(request.name());
        trip.setDescription(request.description());
        trip.getLegs().clear();
        request.legs().stream().map(TripLeg::new).forEach(trip.getLegs()::add);
    }

    private void validateRoute(List<TripLegRequest> legs) {
        Instant previousArrival = null;
        for (TripLegRequest leg : legs) {
            Instant departure = toInstant(leg.departureTime(), leg.departureZone());
            Instant arrival = toInstant(leg.arrivalTime(), leg.arrivalZone());
            if (!arrival.isAfter(departure)) {
                throw new TripException(BAD_REQUEST, "Arrival must be after departure");
            }
            if (previousArrival != null && departure.isBefore(previousArrival)) {
                throw new TripException(BAD_REQUEST, "Route legs must not overlap and must be ordered");
            }
            previousArrival = arrival;
        }
    }

    private Instant toInstant(LocalDateTime time, String zoneName) {
        try {
            ZoneId zone = ZoneId.of(zoneName);
            var offsets = zone.getRules().getValidOffsets(time);
            if (offsets.size() != 1) {
                throw new TripException(BAD_REQUEST,
                        "Local time is nonexistent or ambiguous in zone " + zoneName);
            }
            return time.toInstant(offsets.get(0));
        } catch (DateTimeException ex) {
            throw new TripException(BAD_REQUEST, "Invalid time zone: " + zoneName);
        }
    }
}
