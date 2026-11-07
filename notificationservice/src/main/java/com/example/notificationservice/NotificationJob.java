package com.example.notificationservice;

import com.example.sharedlib.notifications.NotificationContracts.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(name = "notifications.enabled", havingValue = "true")
public class NotificationJob {
    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);
    private final NotificationApiClient api;
    private final ReminderDeliveryService deliveries;
    private final Clock clock;

    public NotificationJob(NotificationApiClient api, ReminderDeliveryService deliveries, Clock clock,
                           @Value("${notifications.secret}") String secret,
                           @Value("${notifications.bot-token}") String token) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32 || token.isBlank()) {
            throw new IllegalArgumentException("Set NOTIFICATION_SERVICE_SECRET (32+ bytes) and TELEGRAM_BOT_TOKEN");
        }
        this.api = api;
        this.deliveries = deliveries;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${notifications.poll-interval-ms:60000}", initialDelay = 1000)
    public void check() {
        List<Departure> departures;
        try {
            var now = clock.instant();
            departures = api.departures(now, now.plus(Duration.ofHours(3)));
        } catch (Exception ex) {
            log.warn("Cannot read upcoming departures ({})", ex.getClass().getSimpleName());
            return;
        }
        for (Departure departure : departures) {
            List<Long> users = departure.participantIds().stream().distinct().toList();
            for (int start = 0; start < users.size(); start += 500) {
                try {
                    List<Recipient> recipients = api.recipients(users.subList(start, Math.min(start + 500, users.size())));
                    for (Recipient recipient : recipients) {
                        // Do not trust an unexpected recipient outside the trip.
                        if (!users.contains(recipient.userId())) continue;
                        try {
                            deliveries.deliver(departure, recipient);
                        } catch (Exception ex) {
                            log.warn("Cannot process reminder for trip {}, user {} ({})",
                                    departure.tripId(), recipient.userId(), ex.getClass().getSimpleName());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Cannot read Telegram recipients for trip {} ({})",
                            departure.tripId(), ex.getClass().getSimpleName());
                }
            }
        }
    }
}
