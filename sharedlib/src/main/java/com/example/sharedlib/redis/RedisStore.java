package com.example.sharedlib.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.Duration;
import java.util.function.Supplier;

/** One Redis, explicit namespaces; JSON only, never Java object deserialization. */
public class RedisStore implements AutoCloseable {
    private final JedisPooled redis;
    private final boolean enabled;
    private final JsonMapper json = JsonMapper.builder().build();

    public RedisStore(String url, boolean enabled) {
        this.enabled = enabled;
        var pool = new org.apache.commons.pool2.impl.GenericObjectPoolConfig<redis.clients.jedis.Connection>();
        pool.setMaxTotal(16);
        pool.setMaxWait(Duration.ofSeconds(1));
        this.redis = enabled ? new JedisPooled(pool, URI.create(url), 1000) : null;
    }
    public String get(String key) { return required().get("travelapp:v1:" + key); }
    public void set(String key, String value, Duration ttl) {
        required().set("travelapp:v1:" + key, value, SetParams.setParams().px(ttl.toMillis()));
    }
    public boolean claim(String key, String value, Duration ttl) {
        return "OK".equals(required().set("travelapp:v1:" + key, value, SetParams.setParams().nx().px(ttl.toMillis())));
    }
    private JedisPooled required() {
        if (!enabled) throw new IllegalStateException("Redis is required for message deduplication");
        return redis;
    }
    public <T> T cached(String key, Duration ttl, Class<T> type, Supplier<T> load) {
        boolean available = enabled;
        if (enabled) {
            try { String hit = get("cache:" + key); if (hit != null) return json.readValue(hit, type); }
            catch (RuntimeException ignored) { available = false; }
        }
        T value = load.get();
        if (available && value != null) {
            try { set("cache:" + key, json.writeValueAsString(value), ttl); }
            catch (RuntimeException ignored) { /* Only successful source results are cached. */ }
        }
        return value;
    }
    @Override public void close() { if (redis != null) redis.close(); }

    @Configuration(proxyBeanMethods = false)
    public static class Config {
        @Bean(destroyMethod = "close")
        public RedisStore redisStore(@Value("${travel.redis.url:redis://localhost:6379}") String url,
                                     @Value("${travel.redis.enabled:true}") boolean enabled) {
            return new RedisStore(url, enabled);
        }
    }
}
