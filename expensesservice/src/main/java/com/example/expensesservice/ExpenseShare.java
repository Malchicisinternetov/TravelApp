package com.example.expensesservice;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseShare {
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;
}
