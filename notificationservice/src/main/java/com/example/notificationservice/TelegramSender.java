package com.example.notificationservice;

import org.springframework.web.client.*;
import tools.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;

public class TelegramSender {
    private final RestClient client;
    private final String token;

    public TelegramSender(RestClient client, String token) {
        this.client = client;
        this.token = token;
    }

    public void send(Long telegramId, String message) {
        try {
            JsonNode response = client.post().uri("/bot" + token + "/sendMessage")
                    .body(Map.of("chat_id", telegramId, "text", message))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.path("ok").asBoolean()) {
                throw new NotificationSendException(retryDelay(response));
            }
        } catch (RestClientResponseException ex) {
            JsonNode error = null;
            try {
                error = ex.getResponseBodyAs(JsonNode.class);
            } catch (Exception ignored) {
                // Never include HTTP exceptions in logs: their URL contains the bot token.
            }
            throw new NotificationSendException(retryDelay(error));
        } catch (RestClientException ex) {
            throw new NotificationSendException(Duration.ofMinutes(1));
        }
    }

    private Duration retryDelay(JsonNode response) {
        long seconds = response == null ? 60 : response.path("parameters").path("retry_after").asLong(60);
        return Duration.ofSeconds(Math.max(60, Math.min(seconds, 86400)));
    }
}
