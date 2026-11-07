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
    void internalApiCallsUseServiceSecretAndDeserializeUtcDeparture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> assertThat(request.getURI().getPath()).isEqualTo("/internal/trips/departures"))
                .andExpect(header("X-Notification-Secret", "service-secret"))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess("""
                        [{"tripId":10,"tripName":"Holiday","departurePoint":"Moscow","destinationPoint":"Istanbul",
                        "departureTime":"2026-11-01T13:00:00","departureZone":"Europe/Moscow",
                        "departureAt":"2026-11-01T10:00:00Z","participantIds":[1]}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://backend.test/internal/notifications/telegram-recipients"))
                .andExpect(header("X-Notification-Secret", "service-secret"))
                .andExpect(jsonPath("$.userIds[0]").value(1))
                .andRespond(withSuccess("[{\"userId\":1,\"telegramId\":111}]", MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        NotificationApiClient api = new NotificationApiClient(client, client, "service-secret");
        assertThat(api.departures(Instant.parse("2026-11-01T07:00:00Z"), Instant.parse("2026-11-01T10:00:00Z")))
                .singleElement().satisfies(d -> assertThat(d.departureAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z")));
        assertThat(api.recipients(List.of(1L))).singleElement().satisfies(r -> assertThat(r.telegramId()).isEqualTo(111));
        server.verify();
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
