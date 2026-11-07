package com.example.userservice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UserSettingsRepository extends JpaRepository<UserSettingsEntity, Long> {
    List<UserSettingsEntity> findByUserIdInAndTelegramVerifiedTrueAndTelegramIdIsNotNull(Collection<Long> userIds);
    Optional<UserSettingsEntity> findByUserId(Long userId);
    Optional<UserSettingsEntity> findByTelegramId(Long telegramId);
    void deleteByUserId(Long userId);

}
