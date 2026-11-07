package com.example.tripservice;

import com.example.sharedlib.notifications.NotificationContracts.Departure;
import com.example.sharedlib.security.ServiceSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
public class DepartureController {
    private final DepartureService service;
    private final ServiceSecret secret;

    public DepartureController(DepartureService service, @Value("${security.notifications.secret:}") String secret) {
        this.service = service;
        this.secret = new ServiceSecret(secret);
    }

    @GetMapping("/internal/trips/departures")
    public List<Departure> departures(@RequestHeader(value = "X-Notification-Secret", required = false) String supplied,
                                     @RequestParam Instant from, @RequestParam Instant to) {
        secret.verify(supplied);
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(Duration.ofHours(6)) > 0) {
            throw new TripException(org.springframework.http.HttpStatus.BAD_REQUEST, "Use a time window of at most 6 hours");
        }
        return service.find(from, to);
    }
}
