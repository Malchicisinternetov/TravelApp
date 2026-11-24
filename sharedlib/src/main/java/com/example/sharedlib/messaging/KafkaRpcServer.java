package com.example.sharedlib.messaging;

import com.example.sharedlib.redis.RedisStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;

public class KafkaRpcServer {
    private final RpcEndpoint endpoint;
    private final RedisStore redis;
    private final KafkaTemplate<String, String> kafka;
    private final String service;
    private final JsonMapper json = JsonMapper.builder().build();
    public KafkaRpcServer(RpcEndpoint endpoint, RedisStore redis, KafkaTemplate<String, String> kafka, String service) {
        this.endpoint = endpoint; this.redis = redis; this.kafka = kafka; this.service = service;
    }
    @KafkaListener(topics = "${travel.messaging.request-topic}", groupId = "${spring.application.name}-requests",
            containerFactory = "travelKafkaListenerFactory")
    public void receive(String payload) {
        RpcRequest request;
        try { request = json.readValue(payload, RpcRequest.class); }
        catch (RuntimeException ex) { return; }
        if (request.id() == null || !request.id().matches("[a-f0-9-]{36}")) return;
        RpcReply reply = execute(request);
        // Wait for the broker acknowledgement before allowing the request offset to be committed.
        try { kafka.send("travel.rpc.replies", request.id(), json.writeValueAsString(reply)).get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Reply interrupted"); }
        catch (Exception ex) { throw new IllegalStateException("Reply unavailable"); }
    }
    public RpcReply execute(RpcRequest request) {
        if (request.expiresAt() <= System.currentTimeMillis() || request.expiresAt() > System.currentTimeMillis() + 120000)
            return error(request, 408, "Request expired");
        try { endpoint.authorize(request); }
        catch (RpcException ex) { return error(request, ex.status(), ex.getMessage()); }
        catch (ResponseStatusException ex) { return error(request, ex.getStatusCode().value(), ex.getReason()); }
        catch (Exception ex) { return error(request, 401, "Invalid authentication"); }
        String key = "rpc:" + service + ":" + request.id();
        String fingerprint = fingerprint(request);
        boolean mutation = request.mutation();
        if (mutation) {
            try {
                if (!redis.claim(key, fingerprint + "\nPENDING", Duration.ofMinutes(10))) {
                    String cached = redis.get(key);
                    if (cached == null) return error(request, 503, "Operation result is unavailable");
                    if (!cached.startsWith(fingerprint + "\n")) return error(request, 409, "Request id was already used");
                    String result = cached.substring(fingerprint.length() + 1);
                    return "PENDING".equals(result) ? error(request, 503, "Operation is in progress; check its result") : json.readValue(result, RpcReply.class);
                }
            } catch (RuntimeException ex) { return error(request, 503, "Redis unavailable; operation was not started"); }
        }
        RpcReply reply;
        try { reply = new RpcReply(request.id(), 200, null, json.valueToTree(endpoint.handle(request))); }
        catch (RpcException ex) { reply = error(request, ex.status(), ex.getMessage()); }
        catch (ResponseStatusException ex) { reply = error(request, ex.getStatusCode().value(), ex.getReason()); }
        catch (DataIntegrityViolationException ex) { reply = error(request, 409, "Data conflicts with an existing record"); }
        catch (IllegalArgumentException ex) { reply = error(request, 400, "Invalid request body or parameters"); }
        catch (Exception ex) { reply = error(request, 500, "Internal error"); }
        if (mutation) {
            try { redis.set(key, fingerprint + "\n" + json.writeValueAsString(reply), Duration.ofMinutes(10)); }
            catch (RuntimeException ignored) { /* The PENDING marker prevents an unsafe replay of a committed write. */ }
        }
        return reply;
    }
    private String fingerprint(RpcRequest request) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(request)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private RpcReply error(RpcRequest request, int status, String message) { return new RpcReply(request.id(), status, message, null); }
}
