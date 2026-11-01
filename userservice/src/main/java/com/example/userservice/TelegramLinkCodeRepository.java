package com.example.userservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface TelegramLinkCodeRepository extends JpaRepository<TelegramLinkCode, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TelegramLinkCode c where c.tokenHash = :hash")
    Optional<TelegramLinkCode> findForUpdate(@Param("hash") String hash);
    void deleteByExpiresAtBefore(Instant time);
}
