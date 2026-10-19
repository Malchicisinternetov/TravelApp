package com.example.userservice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {
    Optional<UserSettingsEntity> findByUserId(Long userId);
    Optional<UserSettingsEntity> findByTelegramId(Long telegramId);
    void deleteByUserId(Long userId);

}
