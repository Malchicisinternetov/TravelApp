package com.example.botservice;

import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

public class TelegramClient {
    private final RestClient client;
    private final String token;

    public TelegramClient(RestClient client, String token) {
        this.client = client;
        this.token = token;
    }

    public JsonNode updates(long offset) {
        return client.post().uri("/bot" + token + "/getUpdates")
                .body(Map.of("offset", offset, "timeout", 25, "allowed_updates", List.of("message")))
                .retrieve().body(JsonNode.class);
    }

    public void send(long chatId, String text) {
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + 3500, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            client.post().uri("/bot" + token + "/sendMessage")
                    .body(Map.of("chat_id", chatId, "text", text.substring(start, end)))
                    .retrieve().toBodilessEntity();
            start = end;
        }
    }
}
