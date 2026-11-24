package com.example.sharedlib.notifications;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public final class NotificationContracts {
    private NotificationContracts() {}

    public record Departure(Long tripId, String tripName, String departurePoint, String destinationPoint,
                            LocalDateTime departureTime, String departureZone, Instant departureAt,
                            List<Long> participantIds) {}

    public record Recipient(Long userId, Long telegramId) {}
}
