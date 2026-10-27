package com.example.expensesservice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses", indexes = @Index(name = "idx_expenses_trip", columnList = "trip_id"))
@Getter
@Setter
public class ExpenseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "trip_id", nullable = false)
    private Long tripId;
    @Column(nullable = false)
    private Long paidBy;
    @Column(nullable = false)
    private Long createdBy;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private LocalDate spentOn;
    @Column(nullable = false, length = 3)
    private String currency;
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;

    @ElementCollection
    @CollectionTable(name = "expense_shares", joinColumns = @JoinColumn(name = "expense_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"expense_id", "user_id"}))
    @OrderColumn(name = "share_order")
    private List<ExpenseShare> shares = new ArrayList<>();
}
