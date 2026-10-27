package com.example.expensesservice;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
    List<ExpenseEntity> findByTripIdOrderBySpentOnDescIdDesc(Long tripId);
    Optional<ExpenseEntity> findByIdAndTripId(Long id, Long tripId);
}
