package com.example.notificationservice;

import com.example.sharedlib.messaging.KafkaRpcClient;
import com.example.sharedlib.notifications.NotificationContracts.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NotificationApiClient {
    private final KafkaRpcClient rpc;
    private final String secret;
    private final JsonMapper json = JsonMapper.builder().build();
    public NotificationApiClient(KafkaRpcClient rpc, String secret) { this.rpc = rpc; this.secret = secret; }
    public List<Departure> departures(Instant from, Instant to) {
        var response = rpc.call("trips", "GET", "/internal/trips/departures", null, secret, Map.of("from", from, "to", to));
        if (response == null || !response.isArray()) throw new IllegalStateException("Empty trip service response");
        return Arrays.asList(json.treeToValue(response, Departure[].class));
    }
    public List<Recipient> recipients(List<Long> userIds) {
        var response = rpc.call("users", "POST", "/internal/notifications/telegram-recipients", null, secret, Map.of("userIds", userIds));
        if (response == null || !response.isArray()) throw new IllegalStateException("Empty user service response");
        return Arrays.asList(json.treeToValue(response, Recipient[].class));
    }
}
