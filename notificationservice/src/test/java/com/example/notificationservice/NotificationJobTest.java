package com.example.notificationservice;

import com.example.sharedlib.notifications.NotificationContracts.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NotificationJobTest {
    private final NotificationApiClient api = mock(NotificationApiClient.class);
    private final ReminderDeliveryService deliveries = mock(ReminderDeliveryService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-01T07:00:00Z"), ZoneOffset.UTC);
    private final NotificationJob job = new NotificationJob(api, deliveries, clock, "a".repeat(32), "test-token");
    private final Departure departure = new Departure(10L, "Holiday", "Москва", "Стамбул",
            LocalDateTime.parse("2026-11-01T13:00:00"), "Europe/Moscow",
            clock.instant().plusSeconds(10800), List.of(1L, 2L));

    @Test
    void requestsThreeHourWindowAndContinuesAfterOneRecipientFails() {
        when(api.departures(clock.instant(), clock.instant().plusSeconds(10800))).thenReturn(List.of(departure));
        when(api.recipients(List.of(1L, 2L))).thenReturn(List.of(new Recipient(1L, 111L), new Recipient(2L, 222L)));
        when(deliveries.deliver(departure, new Recipient(1L, 111L))).thenThrow(new RuntimeException("DB failure"));
        job.check();
        verify(deliveries).deliver(departure, new Recipient(2L, 222L));
    }

    @Test
    void ignoresUnexpectedRecipientsOutsideTheTrip() {
        when(api.departures(any(), any())).thenReturn(List.of(departure));
        when(api.recipients(anyList())).thenReturn(List.of(new Recipient(99L, 999L)));
        job.check();
        verifyNoInteractions(deliveries);
    }

    @Test
    void unavailableTripServiceDoesNotSendAnything() {
        when(api.departures(any(), any())).thenThrow(new RuntimeException("Unavailable"));
        job.check();
        verifyNoInteractions(deliveries);
        verify(api, never()).recipients(anyList());
    }
}
