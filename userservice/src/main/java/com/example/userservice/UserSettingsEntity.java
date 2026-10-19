package com.example.userservice;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "user") // чтобы не было stack overflow
public class UserSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "telegram_id", unique = true) // Индекс для быстрого поиска
    private Long telegramId;

    private String theme;
    private String language;
    private String notificationMethod;
    
}
