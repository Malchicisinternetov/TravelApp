package com.example.notificationservice;

import com.example.sharedlib.notifications.NotificationContracts.*;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NotificationApiClient {
    private final RestClient trips;
    private final RestClient users;
    private final String secret;

    public NotificationApiClient(RestClient trips, RestClient users, String secret) {
        this.trips = trips;
        this.users = users;
        this.secret = secret;
    }

    public List<Departure> departures(Instant from, Instant to) {
        Departure[] response = trips.get().uri(uri -> uri.path("/internal/trips/departures")
                        .queryParam("from", from.toString()).queryParam("to", to.toString()).build())
                .header("X-Notification-Secret", secret).retrieve().body(Departure[].class);
        if (response == null) throw new IllegalStateException("Empty trip service response");
        return Arrays.asList(response);
    }

    public List<Recipient> recipients(List<Long> userIds) {
        Recipient[] response = users.post().uri("/internal/notifications/telegram-recipients")
                .header("X-Notification-Secret", secret).body(Map.of("userIds", userIds))
                .retrieve().body(Recipient[].class);
        if (response == null) throw new IllegalStateException("Empty user service response");
        return Arrays.asList(response);
    }
}
