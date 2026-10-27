package com.example.expensesservice;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class ExpenseService {
    private final ExpenseRepository repository;
    private final TripClient trips;

    public ExpenseService(ExpenseRepository repository, TripClient trips) {
        this.repository = repository;
        this.trips = trips;
    }

    @Transactional
    public ExpenseResponse create(Long tripId, Long actorId, String authorization, ExpenseRequest request) {
        List<TripClient.Participant> participants = trips.participants(tripId, authorization);
        requireWriter(requireParticipant(participants, actorId));
        Set<Long> memberIds = new HashSet<>();
        participants.forEach(p -> memberIds.add(p.userId()));
        if (!memberIds.contains(request.paidBy())) {
            throw new ExpenseException(BAD_REQUEST, "Payer must be a trip participant");
        }
        int scale = currencyScale(request.currency());
        BigDecimal amount = money(request.amount(), scale);
        List<ExpenseShare> shares = split(request, amount, scale);
        Set<Long> seen = new HashSet<>();
        for (ExpenseShare share : shares) {
            if (!memberIds.contains(share.getUserId()) || !seen.add(share.getUserId())) {
                throw new ExpenseException(BAD_REQUEST, "Shares must refer to distinct trip participants");
            }
        }

        ExpenseEntity expense = new ExpenseEntity();
        expense.setTripId(tripId);
        expense.setPaidBy(request.paidBy());
        expense.setCreatedBy(actorId);
        expense.setDescription(request.description());
        expense.setSpentOn(request.spentOn());
        expense.setCurrency(request.currency());
        expense.setAmount(amount);
        expense.getShares().addAll(shares);
        return ExpenseResponse.from(repository.save(expense));
    }

    public List<ExpenseResponse> history(Long tripId, Long actorId, String authorization, boolean mine) {
        requireParticipant(trips.participants(tripId, authorization), actorId);
        return repository.findByTripIdOrderBySpentOnDescIdDesc(tripId).stream()
                .filter(e -> !mine || e.getPaidBy().equals(actorId)
                        || e.getShares().stream().anyMatch(s -> s.getUserId().equals(actorId)))
                .map(ExpenseResponse::from).toList();
    }

    public List<ExpenseSummary> summary(Long tripId, Long actorId, String authorization, boolean mine) {
        List<TripClient.Participant> participants = trips.participants(tripId, authorization);
        requireParticipant(participants, actorId);
        List<ExpenseSummary> result = ExpenseCalculator.summarize(
                repository.findByTripIdOrderBySpentOnDescIdDesc(tripId),
                participants.stream().map(TripClient.Participant::userId).toList());
        if (!mine) {
            return result;
        }
        return result.stream().map(s -> new ExpenseSummary(s.currency(),
                s.participants().stream().filter(p -> p.userId().equals(actorId)).toList(),
                s.debts().stream().filter(d -> d.fromUserId().equals(actorId) || d.toUserId().equals(actorId))
                        .toList())).toList();
    }

    @Transactional
    public void delete(Long tripId, Long expenseId, Long actorId, String authorization) {
        TripClient.Participant actor = requireParticipant(trips.participants(tripId, authorization), actorId);
        requireWriter(actor);
        ExpenseEntity expense = repository.findByIdAndTripId(expenseId, tripId)
                .orElseThrow(() -> new ExpenseException(NOT_FOUND, "Expense not found: " + expenseId));
        if (!"OWNER".equals(actor.role()) && !expense.getCreatedBy().equals(actorId)) {
            throw new ExpenseException(FORBIDDEN, "Only the creator or a trip owner can delete an expense");
        }
        repository.delete(expense);
    }

    private List<ExpenseShare> split(ExpenseRequest request, BigDecimal amount, int scale) {
        if ((request.participantIds() == null) == (request.shares() == null)) {
            throw new ExpenseException(BAD_REQUEST, "Provide either participantIds or shares");
        }
        List<ExpenseShare> shares = new ArrayList<>();
        if (request.shares() != null) {
            request.shares().forEach(s -> shares.add(new ExpenseShare(s.userId(), money(s.amount(), scale))));
        } else {
            if (request.participantIds().isEmpty()) {
                throw new ExpenseException(BAD_REQUEST, "At least one participant is required");
            }
            int count = request.participantIds().size();
            BigDecimal base = amount.divide(BigDecimal.valueOf(count), scale, RoundingMode.DOWN);
            int extraUnits = amount.subtract(base.multiply(BigDecimal.valueOf(count)))
                    .movePointRight(scale).intValueExact();
            BigDecimal unit = BigDecimal.ONE.movePointLeft(scale);
            // Give the indivisible remainder to the first participants in request order.
            for (int i = 0; i < count; i++) {
                shares.add(new ExpenseShare(request.participantIds().get(i),
                        i < extraUnits ? base.add(unit) : base));
            }
        }
        BigDecimal total = shares.stream().map(ExpenseShare::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (shares.isEmpty() || total.compareTo(amount) != 0) {
            throw new ExpenseException(BAD_REQUEST, "Shares must add up to the expense amount");
        }
        return shares;
    }

    private int currencyScale(String currency) {
        try {
            int scale = Currency.getInstance(currency).getDefaultFractionDigits();
            if (scale < 0 || scale > 3) {
                throw new IllegalArgumentException();
            }
            return scale;
        } catch (IllegalArgumentException ex) {
            throw new ExpenseException(BAD_REQUEST, "Unsupported currency: " + currency);
        }
    }

    private BigDecimal money(BigDecimal amount, int scale) {
        try {
            return amount.setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ExpenseException(BAD_REQUEST, "Amount has too many decimal places for this currency");
        }
    }

    private TripClient.Participant requireParticipant(List<TripClient.Participant> participants, Long userId) {
        return participants.stream().filter(p -> userId.equals(p.userId())).findFirst()
                .orElseThrow(() -> new ExpenseException(FORBIDDEN, "User is not a trip participant"));
    }

    private void requireWriter(TripClient.Participant participant) {
        if (!Set.of("OWNER", "MEMBER").contains(participant.role())) {
            throw new ExpenseException(FORBIDDEN, "Read-only participants cannot modify expenses");
        }
    }
}
