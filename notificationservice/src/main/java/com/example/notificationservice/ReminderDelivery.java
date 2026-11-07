package com.example.notificationservice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
@Table(name = "reminder_deliveries")
public class ReminderDelivery {
    @Id
    @Column(length = 64)
    private String id;
    @Column(nullable = false)
    private Long tripId;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Instant departureAt;
    private Instant sentAt;
    private Instant retryAt;
}
