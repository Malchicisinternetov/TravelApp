package com.example.expensesservice;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:expenses;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=test-only-signing-key-at-least-32-bytes-long"
})
@AutoConfigureMockMvc
class ExpensesserviceApplicationTests {
    private static final String BASE = "/trips/10/expenses";
    private static final List<TripClient.Participant> PARTICIPANTS = List.of(
            new TripClient.Participant(1L, "OWNER"), new TripClient.Participant(2L, "MEMBER"),
            new TripClient.Participant(3L, "MEMBER"), new TripClient.Participant(4L, "READ_ONLY"));
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ExpenseRepository repository;
    @Autowired SecretKey jwtSigningKey;
    @MockitoBean TripClient trips;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(trips.participants(anyLong(), anyString())).thenReturn(PARTICIPANTS);
    }

    @Test
    void fourTicketsAreStoredAndSplitBetweenAllParticipants() throws Exception {
        ExpenseResponse expense = create(1, equal("400", "RUB", 1, 1L, 2L, 3L, 4L));
        assertThat(expense.tripId()).isEqualTo(10);
        assertThat(expense.createdBy()).isEqualTo(1);
        assertThat(expense.shares()).hasSize(4);
        expense.shares().forEach(s -> assertThat(s.amount()).isEqualByComparingTo("100"));
        mvc.perform(get(BASE).header("Authorization", bearer(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].description").value("Tickets"))
                .andExpect(jsonPath("$[0].spentOn").value("2026-10-25"))
                .andExpect(jsonPath("$[0].shares.length()").value(4));
        ExpenseSummary summary = summary(1, false)[0];
        assertThat(total(summary, 1).paid()).isEqualByComparingTo("400");
        assertThat(total(summary, 1).share()).isEqualByComparingTo("100");
        assertThat(total(summary, 1).balance()).isEqualByComparingTo("300");
        assertThat(summary.debts()).hasSize(3);
        for (ExpenseSummary.Debt debt : summary.debts()) {
            assertThat(debt.toUserId()).isEqualTo(1);
            assertThat(debt.amount()).isEqualByComparingTo("100");
        }
        assertBalanced(summary);
    }

    @Test
    void reciprocalDebtsAreOffsetAndPersonalSummaryShowsBothDirections() throws Exception {
        create(1, equal("400", "RUB", 1, 1L, 2L, 3L, 4L));
        create(2, equal("100", "RUB", 2, 1L, 2L));
        create(2, equal("60", "RUB", 2, 2L, 3L));
        ExpenseSummary mine = summary(2, true)[0];
        assertThat(mine.participants()).hasSize(1);
        assertThat(total(mine, 2).paid()).isEqualByComparingTo("160");
        assertThat(total(mine, 2).share()).isEqualByComparingTo("180");
        assertThat(total(mine, 2).balance()).isEqualByComparingTo("-20");
        assertDebt(mine, 2, 1, "50");
        assertDebt(mine, 3, 2, "30");
        assertThat(mine.debts()).hasSize(2);
        assertBalanced(summary(1, false)[0]);
    }

    @Test
    void differentCurrenciesAreNotAddedOrOffsetTogether() throws Exception {
        create(1, equal("100", "RUB", 1, 2L));
        create(2, equal("20", "USD", 2, 1L));
        ExpenseSummary[] summaries = summary(1, false);
        assertThat(summaries).hasSize(2);
        assertThat(summaries[0].currency()).isEqualTo("RUB");
        assertDebt(summaries[0], 2, 1, "100");
        assertThat(summaries[1].currency()).isEqualTo("USD");
        assertDebt(summaries[1], 1, 2, "20");
    }

    @Test
    void equalReciprocalExpensesLeaveNoDebt() throws Exception {
        create(1, equal("10", "RUB", 1, 2L));
        create(2, equal("10", "RUB", 2, 1L));
        ExpenseSummary summary = summary(1, false)[0];
        assertThat(summary.debts()).isEmpty();
        assertThat(total(summary, 1).balance()).isEqualByComparingTo("0");
        assertThat(total(summary, 2).balance()).isEqualByComparingTo("0");
    }

    @Test
    void equalSplitPreservesMinorUnitsForDifferentCurrencies() throws Exception {
        for (String[] sample : new String[][]{
                {"10", "RUB", "3.34", "3.33"}, {"100", "JPY", "34", "33"},
                {"1", "KWD", "0.334", "0.333"}, {"0.01", "USD", "0.01", "0.00"}}) {
            ExpenseResponse expense = create(1, equal(sample[0], sample[1], 1, 1L, 2L, 3L));
            assertThat(expense.shares().get(0).amount()).isEqualByComparingTo(sample[2]);
            assertThat(expense.shares().get(1).amount()).isEqualByComparingTo(sample[3]);
            assertThat(expense.shares().stream().map(ExpenseRequest.Share::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(expense.amount());
        }
    }

    @Test
    void customSharesAllowPayerToPayOnlyForOthers() throws Exception {
        Map<String, Object> request = equal("90", "EUR", 1);
        request.remove("participantIds");
        request.put("shares", List.of(Map.of("userId", 2, "amount", 20), Map.of("userId", 3, "amount", 70)));
        create(1, request);
        ExpenseSummary summary = summary(1, false)[0];
        assertDebt(summary, 2, 1, "20");
        assertDebt(summary, 3, 1, "70");
        assertThat(total(summary, 1).share()).isEqualByComparingTo("0");
        assertBalanced(summary);
    }

    @Test
    void personalHistoryContainsPaidExpensesAndAssignedSharesOnly() throws Exception {
        create(1, equal("100", "RUB", 1, 2L));
        create(2, equal("50", "RUB", 2, 3L));
        create(1, equal("40", "RUB", 1, 4L));
        mvc.perform(get(BASE + "/me").header("Authorization", bearer(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(BASE).header("Authorization", bearer(2)))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/trips/20/expenses").header("Authorization", bearer(2)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void invalidCurrencyAmountsAndParticipantsAreRejectedWithoutSaving() throws Exception {
        for (Map<String, Object> request : List.of(
                equal("0", "RUB", 1, 1L), equal("-1", "RUB", 1, 1L),
                equal("1.001", "USD", 1, 1L), equal("1.5", "JPY", 1, 1L),
                equal("10", "ZZZ", 1, 1L), equal("10", "RUB", 99, 1L),
                equal("10", "RUB", 1, 99L), equal("10", "RUB", 1, 1L, 1L),
                equal("10", "RUB", 1))) {
            postExpense(1, request).andExpect(status().isBadRequest());
        }
        Map<String, Object> mixed = equal("10", "RUB", 1, 1L);
        mixed.put("shares", List.of(Map.of("userId", 1, "amount", 10)));
        postExpense(1, mixed).andExpect(status().isBadRequest());
        mixed.remove("participantIds");
        mixed.put("shares", List.of(Map.of("userId", 1, "amount", 9)));
        postExpense(1, mixed).andExpect(status().isBadRequest());
        mixed.put("shares", List.of(Map.of("userId", 1, "amount", -1), Map.of("userId", 2, "amount", 11)));
        postExpense(1, mixed).andExpect(status().isBadRequest());
        mixed.put("shares", Arrays.asList((Object) null));
        postExpense(1, mixed).andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void jwtAndCurrentTripMembershipAreRequired() throws Exception {
        mvc.perform(get(BASE).header("X-User-Id", 1)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).header("Authorization", bearer(9))).andExpect(status().isForbidden());
        postExpense(9, equal("10", "RUB", 1, 1L)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/summary/me").header("Authorization", bearer(9)))
                .andExpect(status().isForbidden());
        String token = bearer(2);
        mvc.perform(get(BASE).header("Authorization", token)).andExpect(status().isOk());
        verify(trips).participants(10L, token);
    }

    @Test
    void readOnlyParticipantCanReadButCannotCreateOrDelete() throws Exception {
        ExpenseResponse expense = create(1, equal("10", "RUB", 1, 4L));
        mvc.perform(get(BASE).header("Authorization", bearer(4))).andExpect(status().isOk());
        postExpense(4, equal("10", "RUB", 4, 4L)).andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/" + expense.id()).header("Authorization", bearer(4)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletionIsLimitedToCreatorOrOwnerAndRecalculatesDebts() throws Exception {
        ExpenseResponse expense = create(2, equal("100", "RUB", 2, 1L, 2L));
        mvc.perform(delete(BASE + "/" + expense.id()).header("Authorization", bearer(3)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/trips/20/expenses/" + expense.id()).header("Authorization", bearer(1)))
                .andExpect(status().isNotFound());
        mvc.perform(delete(BASE + "/" + expense.id()).header("Authorization", bearer(2)))
                .andExpect(status().isNoContent());
        assertThat(summary(1, false)).isEmpty();
        ExpenseResponse another = create(2, equal("50", "USD", 2, 3L));
        mvc.perform(delete(BASE + "/" + another.id()).header("Authorization", bearer(1)))
                .andExpect(status().isNoContent());
        assertThat(repository.count()).isZero();
    }

    @Test
    void historicalSharesSurviveParticipantRemovalButRemovedUserLosesAccess() throws Exception {
        create(1, equal("100", "RUB", 1, 2L));
        when(trips.participants(anyLong(), anyString())).thenReturn(List.of(PARTICIPANTS.get(0)));
        assertDebt(summary(1, false)[0], 2, 1, "100");
        mvc.perform(get(BASE).header("Authorization", bearer(2))).andExpect(status().isForbidden());
        postExpense(1, equal("10", "RUB", 1, 2L)).andExpect(status().isBadRequest());
    }

    @Test
    void tripServiceFailureOrMissingTripCannotCreateExpenses() throws Exception {
        when(trips.participants(anyLong(), anyString()))
                .thenThrow(new ExpenseException(SERVICE_UNAVAILABLE, "Trip service is unavailable"));
        postExpense(1, equal("10", "RUB", 1, 1L)).andExpect(status().isServiceUnavailable());
        doThrow(new ExpenseException(NOT_FOUND, "Trip not found"))
                .when(trips).participants(anyLong(), anyString());
        mvc.perform(get(BASE).header("Authorization", bearer(1))).andExpect(status().isNotFound());
        assertThat(repository.count()).isZero();
    }

    private Map<String, Object> equal(String amount, String currency, long payer, Long... participants) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("description", "Tickets");
        request.put("spentOn", "2026-10-25");
        request.put("paidBy", payer);
        request.put("amount", new BigDecimal(amount));
        request.put("currency", currency);
        request.put("participantIds", List.of(participants));
        return request;
    }

    private ResultActions postExpense(long actor, Map<String, Object> request) throws Exception {
        return mvc.perform(post(BASE).header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)));
    }

    private ExpenseResponse create(long actor, Map<String, Object> request) throws Exception {
        return mapper.readValue(postExpense(actor, request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), ExpenseResponse.class);
    }

    private ExpenseSummary[] summary(long actor, boolean mine) throws Exception {
        String response = mvc.perform(get(BASE + (mine ? "/summary/me" : "/summary"))
                        .header("Authorization", bearer(actor))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readValue(response, ExpenseSummary[].class);
    }

    private ExpenseSummary.ParticipantTotal total(ExpenseSummary summary, long id) {
        return summary.participants().stream().filter(p -> p.userId() == id).findFirst().orElseThrow();
    }

    private void assertDebt(ExpenseSummary summary, long from, long to, String amount) {
        var debt = summary.debts().stream().filter(d -> d.fromUserId() == from && d.toUserId() == to)
                .findFirst().orElseThrow();
        assertThat(debt.amount()).isEqualByComparingTo(amount);
    }

    private void assertBalanced(ExpenseSummary summary) {
        assertThat(summary.participants().stream().map(ExpenseSummary.ParticipantTotal::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    private String bearer(long userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(Long.toString(userId)).issuer("travelapp")
                .audience(List.of("travelapp-api")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300)).build();
        return "Bearer " + new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
