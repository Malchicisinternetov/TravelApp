package com.example.sharedlib.messaging;

import tools.jackson.databind.JsonNode;

public record RpcReply(String id, int status, String message, JsonNode body) {}
