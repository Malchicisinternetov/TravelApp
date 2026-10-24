package com.example.tripservice;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<TripEntity, Long> {
    @Query("select distinct t from TripEntity t join t.participants p where p.userId = :userId order by t.id")
    List<TripEntity> findAllForUser(@Param("userId") Long userId);

    // Serialize aggregate writes, including concurrent removal/demotion of owners.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TripEntity t where t.id = :id")
    Optional<TripEntity> findForUpdate(@Param("id") Long id);
}
