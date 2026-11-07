package com.example.tripservice;

import com.example.sharedlib.notifications.NotificationContracts.Departure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DepartureService {
    private final TripRepository trips;

    public DepartureService(TripRepository trips) {
        this.trips = trips;
    }

    @Transactional(readOnly = true)
    public List<Departure> find(Instant from, Instant to) {
        List<Departure> result = new ArrayList<>();
        // Every ZoneId offset lies within +/-18h. Use local-time bounds for the DB query,
        // then compare actual instants to handle departures in different time zones.
        LocalDateTime earliest = LocalDateTime.ofInstant(from, ZoneOffset.UTC).minusHours(18);
        LocalDateTime latest = LocalDateTime.ofInstant(to, ZoneOffset.UTC).plusHours(18);
        for (TripEntity trip : trips.findWithDeparturesBetween(earliest, latest)) {
            for (TripLeg leg : trip.getLegs()) {
                var offsets = ZoneId.of(leg.getDepartureZone()).getRules().getValidOffsets(leg.getDepartureTime());
                if (offsets.size() != 1) continue;
                Instant departureAt = leg.getDepartureTime().toInstant(offsets.get(0));
                if (departureAt.isBefore(from) || departureAt.isAfter(to)) continue;
                result.add(new Departure(trip.getId(), trip.getName(), leg.getDeparturePoint(),
                        leg.getDestinationPoint(), leg.getDepartureTime(), leg.getDepartureZone(), departureAt,
                        trip.getParticipants().stream().map(TripParticipant::getUserId).toList()));
            }
        }
        result.sort(Comparator.comparing(Departure::departureAt).thenComparing(Departure::tripId));
        return result;
    }
}
