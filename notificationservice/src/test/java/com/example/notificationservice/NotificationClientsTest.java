package com.example.notificationservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NotificationClientsTest {
    @Test
    void internalQueriesUseKafkaAndTheNotificationSecret() {
        var rpc = org.mockito.Mockito.mock(com.example.sharedlib.messaging.KafkaRpcClient.class);
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        Instant from = Instant.parse("2026-11-01T07:00:00Z");
        Instant to = Instant.parse("2026-11-01T10:00:00Z");
        org.mockito.Mockito.when(rpc.call("trips", "GET", "/internal/trips/departures", null, "service-secret", java.util.Map.of("from", from, "to", to)))
                .thenReturn(json.readTree("[{\"tripId\":10,\"departureAt\":\"2026-11-01T10:00:00Z\",\"participantIds\":[1]}]"));
        org.mockito.Mockito.when(rpc.call("users", "POST", "/internal/notifications/telegram-recipients", null, "service-secret", java.util.Map.of("userIds", List.of(1L))))
                .thenReturn(json.readTree("[{\"userId\":1,\"telegramId\":111}]"));
        var api = new NotificationApiClient(rpc, "service-secret");
        assertThat(api.departures(from, to)).singleElement().satisfies(d -> assertThat(d.departureAt()).isEqualTo(to));
        assertThat(api.recipients(List.of(1L))).singleElement().satisfies(r -> assertThat(r.telegramId()).isEqualTo(111));
    }
    @Test
    void telegramSendsPlainMessageThroughExistingBotToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bottoken/sendMessage"))
                .andExpect(jsonPath("$.chat_id").value(111))
                .andExpect(jsonPath("$.text").value("Скоро ваш вылет!"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        new TelegramSender(builder.build(), "token").send(111L, "Скоро ваш вылет!");
        server.verify();
    }

    @Test
    void telegramRateLimitIsRetriedAfterItsRequestedDelayWithoutLeakingToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("{\"ok\":false,\"parameters\":{\"retry_after\":120}}"));
        assertThatThrownBy(() -> new TelegramSender(builder.build(), "secret-token").send(111L, "Reminder"))
                .isInstanceOfSatisfying(NotificationSendException.class,
                        ex -> assertThat(ex.getRetryDelay()).isEqualTo(Duration.ofSeconds(120)))
                .hasMessageNotContaining("secret-token");
    }

    @Test
    void unsuccessfulTelegramResponseIsNotTreatedAsDelivered() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withSuccess("{\"ok\":false}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new TelegramSender(builder.build(), "token").send(111L, "Reminder"))
                .isInstanceOf(NotificationSendException.class);
    }
}
