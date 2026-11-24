package com.example.userservice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, String> {
    void deleteByExpiresAtBefore(Instant now);
}
