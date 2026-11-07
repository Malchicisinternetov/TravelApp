package com.example.notificationservice;

import com.example.sharedlib.notifications.NotificationContracts.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reminders;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "notifications.enabled=false"
})
class NotificationServiceApplicationTests {
    private static final Instant NOW = Instant.parse("2026-11-01T07:00:00Z");
    @Autowired ReminderDeliveryService service;
    @Autowired ReminderDeliveryRepository repository;
    @MockitoBean TelegramSender telegram;
    @MockitoBean Clock clock;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(clock.instant()).thenReturn(NOW);
    }

    private Departure departure(Instant time) {
        return new Departure(10L, "Holiday", "Москва", "Стамбул",
                LocalDateTime.ofInstant(time, ZoneId.of("Europe/Moscow")),
                "Europe/Moscow", time, List.of(1L, 2L));
    }

    @Test
    void sendsAtThreeHoursAndPersistsDeliveryToPreventRepeatedMessages() {
        Departure departure = departure(NOW.plusSeconds(10800));
        assertThat(service.deliver(departure, new Recipient(1L, 111L))).isTrue();
        assertThat(service.deliver(departure, new Recipient(1L, 111L))).isFalse();
        verify(telegram).send(eq(111L), contains("Скоро ваш вылет из Москва в Стамбул, не пропустите!"));
        verify(telegram).send(eq(111L), contains("01.11.2026 13:00 (Europe/Moscow)"));
        assertThat(repository.findAll()).singleElement().satisfies(row -> assertThat(row.getSentAt()).isEqualTo(NOW));
    }

    @Test
    void everyParticipantHasTheirOwnDeliveryState() {
        Departure departure = departure(NOW.plusSeconds(10800));
        service.deliver(departure, new Recipient(1L, 111L));
        service.deliver(departure, new Recipient(2L, 222L));
        verify(telegram).send(eq(111L), anyString());
        verify(telegram).send(eq(222L), anyString());
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void doesNotSendTooEarlyOrAtOrAfterDeparture() {
        for (Instant time : List.of(NOW.plusSeconds(10801), NOW, NOW.minusSeconds(1))) {
            assertThat(service.deliver(departure(time), new Recipient(1L, 111L))).isFalse();
        }
        verifyNoInteractions(telegram);
        assertThat(repository.count()).isZero();
    }

    @Test
    void catchesUpWhenStartedInsideReminderWindow() {
        assertThat(service.deliver(departure(NOW.plusSeconds(3600)), new Recipient(1L, 111L))).isTrue();
    }

    @Test
    void failedSendIsRetriedAfterDelayAndDoesNotMarkReminderAsSent() {
        Departure departure = departure(NOW.plusSeconds(10800));
        doThrow(new NotificationSendException(Duration.ofSeconds(120))).doNothing()
                .when(telegram).send(eq(111L), anyString());
        assertThat(service.deliver(departure, new Recipient(1L, 111L))).isFalse();
        assertThat(repository.findAll().get(0).getSentAt()).isNull();
        assertThat(service.deliver(departure, new Recipient(1L, 111L))).isFalse();
        verify(telegram, times(1)).send(eq(111L), anyString());
        when(clock.instant()).thenReturn(NOW.plusSeconds(120));
        assertThat(service.deliver(departure, new Recipient(1L, 111L))).isTrue();
        verify(telegram, times(2)).send(eq(111L), anyString());
        assertThat(repository.findAll().get(0).getSentAt()).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void changedDepartureGetsNewReminderButRenamingTripDoesNot() {
        Departure original = departure(NOW.plusSeconds(10800));
        service.deliver(original, new Recipient(1L, 111L));
        Departure renamed = new Departure(10L, "New name", original.departurePoint(), original.destinationPoint(),
                original.departureTime(), original.departureZone(), original.departureAt(), List.of(2L, 1L));
        assertThat(service.deliver(renamed, new Recipient(1L, 111L))).isFalse();
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        assertThat(service.deliver(departure(NOW.plusSeconds(14400)), new Recipient(1L, 111L))).isTrue();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void simultaneousWorkersCannotSendTheSameReminderTwice() throws Exception {
        var workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) {
                tasks.add(workers.submit(() -> {
                    try {
                        start.await();
                        service.deliver(departure(NOW.plusSeconds(10800)), new Recipient(1L, 111L));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                        // A concurrent first insert lost the unique-key race.
                    }
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(10, java.util.concurrent.TimeUnit.SECONDS);
            verify(telegram, times(1)).send(eq(111L), anyString());
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            workers.shutdownNow();
        }
    }
}
