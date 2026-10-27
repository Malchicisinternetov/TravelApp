package com.example.expensesservice;

import java.math.BigDecimal;
import java.util.*;
import static com.example.expensesservice.ExpenseSummary.*;

public final class ExpenseCalculator {
    private ExpenseCalculator() {}

    private record Pair(Long first, Long second) {}

    public static List<ExpenseSummary> summarize(List<ExpenseEntity> expenses, List<Long> participantIds) {
        Map<String, List<ExpenseEntity>> currencies = new TreeMap<>();
        expenses.forEach(e -> currencies.computeIfAbsent(e.getCurrency(), key -> new ArrayList<>()).add(e));
        List<ExpenseSummary> result = new ArrayList<>();
        currencies.forEach((currency, items) -> {
            Map<Long, BigDecimal> paid = new TreeMap<>();
            Map<Long, BigDecimal> shares = new TreeMap<>();
            Map<Pair, BigDecimal> netDebts = new HashMap<>();
            participantIds.forEach(id -> paid.put(id, BigDecimal.ZERO));
            for (ExpenseEntity expense : items) {
                Long payer = expense.getPaidBy();
                paid.merge(payer, expense.getAmount(), BigDecimal::add);
                for (ExpenseShare share : expense.getShares()) {
                    Long userId = share.getUserId();
                    paid.putIfAbsent(userId, BigDecimal.ZERO);
                    shares.merge(userId, share.getAmount(), BigDecimal::add);
                    if (!userId.equals(payer)) {
                        Pair pair = new Pair(Math.min(userId, payer), Math.max(userId, payer));
                        BigDecimal signedAmount = userId < payer ? share.getAmount() : share.getAmount().negate();
                        netDebts.merge(pair, signedAmount, BigDecimal::add);
                    }
                }
            }
            List<ParticipantTotal> totals = paid.entrySet().stream().map(entry -> {
                BigDecimal share = shares.getOrDefault(entry.getKey(), BigDecimal.ZERO);
                return new ParticipantTotal(entry.getKey(), entry.getValue(), share, entry.getValue().subtract(share));
            }).toList();
            List<Debt> debts = netDebts.entrySet().stream().filter(e -> e.getValue().signum() != 0)
                    .map(e -> e.getValue().signum() > 0
                            ? new Debt(e.getKey().first(), e.getKey().second(), e.getValue())
                            : new Debt(e.getKey().second(), e.getKey().first(), e.getValue().negate()))
                    .sorted(Comparator.comparing(Debt::fromUserId).thenComparing(Debt::toUserId)).toList();
            result.add(new ExpenseSummary(currency, totals, debts));
        });
        return result;
    }
}
