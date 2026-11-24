package com.example.sharedlib.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

public class KafkaRpcClient {
    private static final Set<String> SERVICES = Set.of("users", "trips", "expenses");
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json = JsonMapper.builder().build();
    private final ConcurrentMap<String, CompletableFuture<RpcReply>> pending = new ConcurrentHashMap<>();
    private final Duration timeout;
    private final boolean enabled;
    public KafkaRpcClient(KafkaTemplate<String, String> kafka, Duration timeout) { this(kafka, timeout, true); }
    public KafkaRpcClient(KafkaTemplate<String, String> kafka, Duration timeout, boolean enabled) {
        this.kafka = kafka; this.timeout = timeout; this.enabled = enabled;
    }

    public JsonNode call(String service, String method, String path, String authorization, String secret, Object body) {
        if (!SERVICES.contains(service)) throw new IllegalArgumentException("Unknown service");
        if (!enabled) throw new RpcException(503, "Kafka is disabled");
        String id = UUID.randomUUID().toString();
        var request = new RpcRequest(id, System.currentTimeMillis() + timeout.toMillis(), method, path,
                authorization, secret, json.valueToTree(body));
        var future = new CompletableFuture<RpcReply>();
        pending.put(id, future);
        try {
            kafka.send("travel.rpc." + service, id, json.writeValueAsString(request))
                    .whenComplete((sent, error) -> { if (error != null) future.completeExceptionally(error); });
            RpcReply reply = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (reply.status() >= 400) throw new RpcException(reply.status(), reply.message());
            return reply.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new RpcException(503, "Service request interrupted");
        } catch (ExecutionException | TimeoutException ex) {
            // A timed out write might already have committed. Never automatically resubmit it under a new id.
            throw new RpcException(503, "Service response unavailable; check the result before repeating the operation");
        } catch (RpcException ex) { throw ex; }
        catch (RuntimeException ex) { throw new RpcException(503, "Service is unavailable"); }
        finally { pending.remove(id); }
    }

    @KafkaListener(topics = "travel.rpc.replies", groupId = "${spring.application.name}-replies-${random.uuid}",
            containerFactory = "travelKafkaListenerFactory")
    public void reply(String payload) {
        try {
            RpcReply reply = json.readValue(payload, RpcReply.class);
            var future = pending.get(reply.id());
            if (future != null) future.complete(reply);
        } catch (RuntimeException ignored) { /* A malformed reply never exposes its payload in logs. */ }
    }
}
