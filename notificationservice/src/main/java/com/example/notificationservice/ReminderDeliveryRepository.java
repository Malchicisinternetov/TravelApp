package com.example.notificationservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ReminderDelivery d where d.id = :id")
    Optional<ReminderDelivery> findForUpdate(@Param("id") String id);
}
