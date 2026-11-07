package com.example.notificationservice;

import com.example.sharedlib.notifications.NotificationContracts.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;

@Service
public class ReminderDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(ReminderDeliveryService.class);
    private final ReminderDeliveryRepository deliveries;
    private final TelegramSender telegram;
    private final Clock clock;

    public ReminderDeliveryService(ReminderDeliveryRepository deliveries, TelegramSender telegram, Clock clock) {
        this.deliveries = deliveries;
        this.telegram = telegram;
        this.clock = clock;
    }

    @Transactional
    public boolean deliver(Departure departure, Recipient recipient) {
        Instant now = clock.instant();
        if (!isDue(departure, now)) return false;
        String key = key(departure, recipient.userId());
        ReminderDelivery delivery = deliveries.findForUpdate(key).orElse(null);
        if (delivery == null) {
            delivery = new ReminderDelivery();
            delivery.setId(key);
            delivery.setTripId(departure.tripId());
            delivery.setUserId(recipient.userId());
            delivery.setDepartureAt(departure.departureAt());
            // Unique key plus row lock serializes concurrent sends of the same reminder.
            delivery = deliveries.saveAndFlush(delivery);
        }
        if (delivery.getSentAt() != null || delivery.getRetryAt() != null && delivery.getRetryAt().isAfter(now)) {
            return false;
        }
        if (!isDue(departure, clock.instant())) return false;
        try {
            String time = departure.departureTime().format(DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm"));
            telegram.send(recipient.telegramId(), "Скоро ваш вылет из " + departure.departurePoint()
                    + " в " + departure.destinationPoint() + ", не пропустите!\nОтправление: "
                    + time + " (" + departure.departureZone() + ").\nПоездка: " + departure.tripName());
            delivery.setSentAt(clock.instant());
            delivery.setRetryAt(null);
            return true;
        } catch (NotificationSendException ex) {
            delivery.setRetryAt(clock.instant().plus(ex.getRetryDelay()));
            log.warn("Reminder delivery failed for trip {}, user {}; will retry", departure.tripId(), recipient.userId());
            return false;
        }
    }

    private boolean isDue(Departure departure, Instant now) {
        return departure.departureAt().isAfter(now)
                && !departure.departureAt().isAfter(now.plus(Duration.ofHours(3)));
    }

    private String key(Departure departure, Long userId) {
        StringBuilder value = new StringBuilder();
        for (String part : List.of(departure.tripId().toString(), userId.toString(), departure.departurePoint(),
                departure.destinationPoint(), departure.departureAt().toString())) {
            value.append(part.length()).append(':').append(part);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
