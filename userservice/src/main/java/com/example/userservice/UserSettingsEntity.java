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

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean telegramVerified;

    private String theme;
    private String language;
    private String notificationMethod;

    public UserSettingsEntity(Long id, UserEntity user, Long telegramId, String theme,
                              String language, String notificationMethod) {
        this.id = id;
        this.user = user;
        this.telegramId = telegramId;
        this.theme = theme;
        this.language = language;
        this.notificationMethod = notificationMethod;
    }
    
}
