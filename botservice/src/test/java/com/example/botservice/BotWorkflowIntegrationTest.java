package com.example.botservice;

import com.example.sharedlib.messaging.KafkaRpcClient;
import com.example.sharedlib.messaging.RpcException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring wiring, command parsing and backend adapter; Kafka is the service boundary. */
@SpringBootTest(properties = {"bot.enabled=false", "bot.name=integration_bot",
        "bot.service-secret=integration-bot-secret-at-least-32-bytes"})
@Tag("integration")
class BotWorkflowIntegrationTest {
    @Autowired BotCommands commands;
    @MockitoBean KafkaRpcClient rpc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach void authenticated() {
        when(rpc.call(eq("users"), eq("POST"), eq("/internal/auth/telegram"),
                isNull(), anyString(), any())).thenReturn(json.valueToTree(Map.of("userId", 42, "accessToken", "test-jwt")));
    }

    @Test void createTripPropagatesAuthenticatedIdentityAndParsedFields() {
        when(rpc.call(eq("trips"), eq("POST"), eq("/trips"), anyString(), isNull(), any()))
                .thenReturn(json.readTree("{\"id\":7,\"name\":\"Holiday\",\"destinationPoint\":\"Rome\",\"legs\":[],\"participants\":[]}"));
        assertThat(commands.execute(777, "Alice", "/trip_create Holiday | Summer | Rome")).contains("Holiday");
        verify(rpc).call("trips", "POST", "/trips", "Bearer test-jwt", null,
                Map.of("name", "Holiday", "description", "Summer", "destinationPoint", "Rome"));
        verify(rpc).call(eq("users"), eq("POST"), eq("/internal/auth/telegram"), isNull(),
                eq("integration-bot-secret-at-least-32-bytes"),
                eq(Map.of("telegramId", 777L, "name", "Alice", "create", false)));
    }

    @Test void backendDenialIsPresentedWithoutLeakingInternalDetails() {
        when(rpc.call(eq("expenses"), anyString(), anyString(), anyString(), isNull(), any()))
                .thenThrow(new RpcException(403, "internal detail"));
        String result = commands.execute(777, "Alice", "/expense_add 7 | 10 USD | Dinner");
        assertThat(result).contains("Недостаточно прав").doesNotContain("internal detail");
    }

    @Test void invalidExpenseArgumentsNeverReachExpenseService() {
        assertThat(commands.execute(777, "Alice", "/expense_add 7 | invalid | Dinner")).contains("сумму и валюту");
        verify(rpc, never()).call(eq("expenses"), anyString(), anyString(), any(), any(), any());
    }

    @Test void helpDoesNotRequireAuthenticationOrCallAnotherService() {
        assertThat(commands.execute(777, "Alice", "/help")).contains("/trip_create");
        verifyNoInteractions(rpc);
    }
}
