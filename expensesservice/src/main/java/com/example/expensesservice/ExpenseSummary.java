package com.example.expensesservice;

import java.math.BigDecimal;
import java.util.List;

public record ExpenseSummary(String currency, List<ParticipantTotal> participants, List<Debt> debts) {
    public record ParticipantTotal(Long userId, BigDecimal paid, BigDecimal share, BigDecimal balance) {}
    public record Debt(Long fromUserId, Long toUserId, BigDecimal amount) {}
}
