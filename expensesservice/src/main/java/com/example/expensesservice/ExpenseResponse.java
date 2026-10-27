package com.example.expensesservice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(Long id, Long tripId, Long paidBy, Long createdBy,
                              String description, LocalDate spentOn, BigDecimal amount,
                              String currency, List<ExpenseRequest.Share> shares) {
    public static ExpenseResponse from(ExpenseEntity expense) {
        return new ExpenseResponse(expense.getId(), expense.getTripId(), expense.getPaidBy(),
                expense.getCreatedBy(), expense.getDescription(), expense.getSpentOn(),
                expense.getAmount(), expense.getCurrency(), expense.getShares().stream()
                .map(s -> new ExpenseRequest.Share(s.getUserId(), s.getAmount())).toList());
    }
}
