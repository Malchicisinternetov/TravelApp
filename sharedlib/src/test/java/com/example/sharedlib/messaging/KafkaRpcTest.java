package com.example.sharedlib.messaging;

import com.example.sharedlib.redis.RedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KafkaRpcTest {
    final JsonMapper json = JsonMapper.builder().build();
    static class MemoryRedis extends RedisStore {
        final Map<String,String> values = new HashMap<>();
        boolean failWrites;
        MemoryRedis() { super("redis://localhost:1", false); }
        @Override public synchronized String get(String key) { return values.get(key); }
        @Override public synchronized boolean claim(String key, String value, Duration ttl) { return values.putIfAbsent(key,value) == null; }
        @Override public void set(String key, String value, Duration ttl) { if (failWrites) throw new IllegalStateException(); values.put(key,value); }
    }
    RpcRequest request() { return new RpcRequest(UUID.randomUUID().toString(), System.currentTimeMillis()+30000,
            "POST","/trips","Bearer jwt",null,json.readTree("{\"name\":\"Holiday\"}")); }
    KafkaRpcServer server(RpcEndpoint endpoint, RedisStore redis) { return new KafkaRpcServer(endpoint, redis, mock(KafkaTemplate.class), "trips"); }

    @Test void duplicateWriteReturnsOriginalResultAndNeverRunsAgain() {
        var calls = new AtomicInteger();
        var server = server(r -> Map.of("id", calls.incrementAndGet()), new MemoryRedis());
        var request = request();
        assertThat(server.execute(request).body().path("id").asInt()).isEqualTo(1);
        assertThat(server.execute(request).body().path("id").asInt()).isEqualTo(1);
        assertThat(calls).hasValue(1);
        var changed = new RpcRequest(request.id(),request.expiresAt(),"DELETE","/trips/1",request.authorization(),null,null);
        assertThat(server.execute(changed).status()).isEqualTo(409);
    }
    @Test void aLostResultCannotCauseACommittedWriteToRunAgain() {
        var redis = new MemoryRedis(); redis.failWrites = true;
        var calls = new AtomicInteger();
        var server = server(r -> calls.incrementAndGet(), redis);
        var request = request();
        assertThat(server.execute(request).status()).isEqualTo(200);
        assertThat(server.execute(request).status()).isEqualTo(503);
        assertThat(calls).hasValue(1);
    }
    @Test void redisOutageFailsWritesClosedButReadsStillWork() {
        var calls = new AtomicInteger();
        var server = server(r -> calls.incrementAndGet(), new RedisStore("redis://localhost:1", false));
        assertThat(server.execute(request()).status()).isEqualTo(503);
        assertThat(calls).hasValue(0);
        var read = new RpcRequest(UUID.randomUUID().toString(),System.currentTimeMillis()+30000,"GET","/trips",null,null,null);
        assertThat(server.execute(read).status()).isEqualTo(200);
    }
    @Test void expiredCommandsAreNeverExecutedAndBusinessErrorsRetainTheirStatus() {
        var calls = new AtomicInteger();
        var server = server(r -> calls.incrementAndGet(), new MemoryRedis());
        assertThat(server.execute(new RpcRequest(UUID.randomUUID().toString(),0,"POST","/trips",null,null,null)).status()).isEqualTo(408);
        assertThat(calls).hasValue(0);
        assertThat(server(r -> { throw new RpcException(403,"Access denied"); },new MemoryRedis()).execute(request()).status()).isEqualTo(403);
    }
    @Test void cachedRepliesStillRequireAuthorization() {
        RpcEndpoint endpoint = new RpcEndpoint() {
            int checks;
            public void authorize(RpcRequest r) { if (++checks > 1) throw new RpcException(401,"Expired token"); }
            public Object handle(RpcRequest r) { return Map.of("id",1); }
        };
        var server = server(endpoint, new MemoryRedis());
        var request = request();
        assertThat(server.execute(request).status()).isEqualTo(200);
        assertThat(server.execute(request).status()).isEqualTo(401);
    }
    @Test void clientCorrelatesReplyAndPreservesServiceErrors() {
        KafkaTemplate<String,String> template = mock(KafkaTemplate.class);
        var client = new KafkaRpcClient(template,Duration.ofSeconds(1));
        when(template.send(anyString(),anyString(),anyString())).thenAnswer(inv -> {
            var request = json.readValue((String) inv.getArgument(2),RpcRequest.class);
            client.reply(json.writeValueAsString(new RpcReply(UUID.randomUUID().toString(),200,null,json.readTree("\"wrong\""))));
            client.reply(json.writeValueAsString(new RpcReply(request.id(),403,"Access denied",null)));
            return CompletableFuture.completedFuture(null);
        });
        assertThatThrownBy(() -> client.call("trips","DELETE","/trips/1","Bearer token",null,null))
                .isInstanceOfSatisfying(RpcException.class,ex -> assertThat(ex.status()).isEqualTo(403));
        verify(template,times(1)).send(eq("travel.rpc.trips"),anyString(),anyString());
    }
    @Test void transportFailureNeverAutomaticallyResendsAMutation() {
        KafkaTemplate<String,String> template = mock(KafkaTemplate.class);
        when(template.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("private payload")));
        var client = new KafkaRpcClient(template,Duration.ofSeconds(1));
        assertThatThrownBy(() -> client.call("expenses","POST","/trips/1/expenses","Bearer secret",null,Map.of("amount","100")))
                .isInstanceOfSatisfying(RpcException.class,ex -> assertThat(ex.status()).isEqualTo(503))
                .hasMessageNotContaining("private payload").hasMessageNotContaining("secret");
        verify(template,times(1)).send(anyString(),anyString(),anyString());
    }
}
