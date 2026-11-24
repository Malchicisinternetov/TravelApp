package com.example.sharedlib.messaging;

import tools.jackson.databind.JsonNode;

public record RpcRequest(String id, long expiresAt, String method, String path,
                         String authorization, String serviceSecret, JsonNode body) {
    public boolean mutation() { return !"GET".equals(method) && !"/users/directory".equals(path)
            && !"/internal/notifications/telegram-recipients".equals(path); }
}
