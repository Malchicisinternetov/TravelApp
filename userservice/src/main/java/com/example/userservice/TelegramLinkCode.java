package com.example.userservice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
public class TelegramLinkCode {
    @Id
    @Column(length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private Instant expiresAt;
}
