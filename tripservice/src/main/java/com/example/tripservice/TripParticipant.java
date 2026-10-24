package com.example.tripservice;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class TripParticipant {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TripRole role;

    public TripParticipant(Long userId, TripRole role) {
        this.userId = userId;
        this.role = role;
    }
}
