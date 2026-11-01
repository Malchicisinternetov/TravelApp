package com.example.botservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpMethod.*;

class BotCommandsTest {
    private final BackendClient api = mock(BackendClient.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final BackendClient.Session session = new BackendClient.Session("secret-jwt", 42);
    private final BotCommands commands = new BotCommands(api, mapper);

    @BeforeEach
    void setUp() {
        when(api.login(anyLong(), anyString(), anyBoolean())).thenReturn(session);
        when(api.request(anyString(), any(), anyString(), any(), any())).thenReturn(mapper.createObjectNode());
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "/me;users;/users/42", "/trips;trips;/trips",
            "/trip 10;trips;/trips/10", "/members 10;trips;/trips/10/participants",
            "/expenses 10;expenses;/trips/10/expenses", "/my_expenses 10;expenses;/trips/10/expenses/me",
            "/summary 10;expenses;/trips/10/expenses/summary",
            "/my_summary 10;expenses;/trips/10/expenses/summary/me"})
    void readCommandsDelegateToServices(String command, String service, String path) {
        commands.execute(777, "Alice", command);
        verify(api).login(777, "Alice", false);
        verify(api).request(service, GET, path, session, null);
    }

    @Test
    void tripAndExpensesAreSentAsInputWithoutCalculations() {
        commands.execute(777, "Alice",
                "/trip_create Holiday | Notes | London");
        JsonNode trip = bodyFor("trips", POST, "/trips");
        assertThat(trip.path("name").asText()).isEqualTo("Holiday");
        assertThat(trip.path("destinationPoint").asText()).isEqualTo("London");
        assertThat(trip.has("legs")).isFalse();
        commands.execute(777, "Alice", "/expense_add 10 | 10,01 usd | Tickets | 42,43,44");
        JsonNode expense = bodyFor("expenses", POST, "/trips/10/expenses");
        assertThat(expense.path("currency").asText()).isEqualTo("USD");
        assertThat(expense.path("participantIds").size()).isEqualTo(3);
        assertThat(expense.has("shares")).isFalse();
        assertThat(expense.has("paidBy")).isFalse();
        assertThat(expense.has("spentOn")).isFalse();
        assertThat(expense.path("amount").decimalValue()).isEqualByComparingTo("10.01");
    }

    @Test
    void membershipChangesAreForwarded() {
        commands.execute(777, "Alice", "/member_add 10 | 43 | ReadOnly");
        assertThat(bodyFor("trips", POST, "/trips/10/participants").path("role").asText()).isEqualTo("READ_ONLY");
        commands.execute(777, "Alice", "/member_role 10 | 43 | OWNER");
        verify(api).request("trips", PUT, "/trips/10/participants/43/role", session, Map.of("role", "OWNER"));
        commands.execute(777, "Alice", "/member_remove 10 | 43");
        verify(api).request("trips", DELETE, "/trips/10/participants/43", session, null);
    }

    @Test
    void routeEditsPreserveOtherTripFieldsAndLegOrder() throws Exception {
        when(api.request("trips", GET, "/trips/10", session, null)).thenReturn(mapper.readTree(
                "{\"name\":\"Holiday\",\"description\":\"Notes\",\"destinationPoint\":\"Rome\",\"legs\":[{\"departurePoint\":\"Moscow\"}]}"));
        commands.execute(777, "Alice", "/leg_add 10 | London | Paris | 02.11.2026 10:00 | Europe/London | 02.11.2026 12:00 | Europe/Paris");
        JsonNode body = bodyFor("trips", PUT, "/trips/10");
        assertThat(body.path("name").asText()).isEqualTo("Holiday");
        assertThat(body.path("legs").size()).isEqualTo(2);
        assertThat(body.path("legs").get(0).path("departurePoint").asText()).isEqualTo("Moscow");
        assertThat(body.path("legs").get(1).path("departureTime").asText()).isEqualTo("2026-11-02T10:00:00");
        assertThat(body.path("destinationPoint").asText()).isEqualTo("Rome");
    }

    @Test
    void shortExpenseLeavesDefaultsToService() {
        commands.execute(777, "Alice", "/expense_add 10 | 1200 RUB | Dinner");
        JsonNode expense = bodyFor("expenses", POST, "/trips/10/expenses");
        assertThat(expense.size()).isEqualTo(3);
        assertThat(expense.path("description").asText()).isEqualTo("Dinner");
        assertThat(expense.has("participantIds")).isFalse();
    }

    @Test
    void removedCommandsAndLinkHelpDoNotCallServices() {
        for (String command : List.of("/settings", "/settings_set", "/expense_split", "/telegram",
                "/link_code", "/telegram_unlink")) {
            assertThat(commands.execute(777, "Alice", command)).contains("Неизвестная команда");
            assertThat(BotCommands.HELP).doesNotContain(command);
        }
        assertThat(commands.execute(777, "Alice", "/link")).contains("/start", "/link код");
        verifyNoInteractions(api);
    }

    @Test
    void unlinkIsAvailableThroughOneLinkCommandAndRequiresConfirmation() {
        assertThat(commands.execute(777, "Alice", "/link отключить")).contains("ДА");
        verifyNoInteractions(api);
        commands.execute(777, "Alice", "/link отключить ДА");
        verify(api).request("users", DELETE, "/auth/telegram", session, null);
    }

    @Test
    void invalidDateAndEmptyParticipantSelectionAreRejectedBeforeWriting() throws Exception {
        when(api.request("trips", GET, "/trips/10", session, null)).thenReturn(mapper.readTree(
                "{\"name\":\"Holiday\",\"destinationPoint\":\"London\",\"legs\":[]}"));
        assertThat(commands.execute(777, "Alice",
                "/leg_add 10 | Moscow | London | 31.02.2026 10:00 | Europe/Moscow | 02.11.2026 12:00 | Europe/London"))
                .contains("Дата и время");
        verify(api, never()).request(eq("trips"), eq(PUT), anyString(), any(), any());
        commands.execute(777, "Alice", "/expense_add 10 | 10 RUB | Dinner | ");
        verify(api, never()).request(eq("expenses"), eq(POST), anyString(), any(), any());
    }

    @Test
    void currencyChangePreservesOtherProfileFields() throws Exception {
        when(api.request("users", GET, "/users/42", session, null)).thenReturn(mapper.readTree(
                "{\"name\":\"Alice\",\"timezone\":\"Europe/Moscow\",\"currency\":\"USD\",\"amount\":100}"));
        commands.execute(777, "Alice", "/currency rub");
        JsonNode body = bodyFor("users", PUT, "/users/42");
        assertThat(body.path("name").asText()).isEqualTo("Alice");
        assertThat(body.path("currency").asText()).isEqualTo("RUB");
        assertThat(body.path("amount").asInt()).isEqualTo(100);
    }

    @Test
    void linkingDoesNotCreateAnExtraAccountOrExposeJwt() {
        when(api.link(777, "one-time-code")).thenReturn(session);
        String reply = commands.execute(777, "Alice", "/start one-time-code");
        assertThat(reply).contains("42").doesNotContain("secret-jwt");
        verify(api).link(777, "one-time-code");
        verify(api, never()).login(anyLong(), anyString(), anyBoolean());
    }

    @Test
    void destructiveActionsRequireExplicitConfirmation() {
        assertThat(commands.execute(777, "Alice", "/trip_delete 10 | нет")).contains("ДА");
        assertThat(commands.execute(777, "Alice", "/account_delete нет")).contains("ДА");
        verify(api, never()).request(anyString(), eq(DELETE), anyString(), any(), any());
        commands.execute(777, "Alice", "/trip_delete 10 | ДА");
        verify(api).request("trips", DELETE, "/trips/10", session, null);
        commands.execute(777, "Alice", "/expense_delete 10 | 99");
        verify(api).request("expenses", DELETE, "/trips/10/expenses/99", session, null);
    }

    @Test
    void invalidArgumentsDoNotSendWriteRequestsAndAccessErrorsAreDisplayed() {
        assertThat(commands.execute(777, "Alice", "/expense_add bad")).contains("/help");
        verify(api, never()).request(anyString(), any(), anyString(), any(), any());
        when(api.request("trips", GET, "/trips/10", session, null)).thenThrow(new BotException("Недостаточно прав"));
        assertThat(commands.execute(777, "Alice", "/trip@travelbot 10")).isEqualTo("Недостаточно прав");
    }

    private JsonNode bodyFor(String service, HttpMethod method, String path) {
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(api).request(eq(service), eq(method), eq(path), eq(session), body.capture());
        return mapper.valueToTree(body.getValue());
    }
}
