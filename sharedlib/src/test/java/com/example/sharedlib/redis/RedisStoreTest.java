package com.example.sharedlib.redis;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

class RedisStoreTest {
    @Test void disabledCacheLoadsDataAndDoesNotRequireRedis() {
        try(var redis=new RedisStore("redis://localhost:1",false)) {
            assertThat(redis.cached("weather:test",Duration.ofMinutes(10),String.class,()->"source")).isEqualTo("source");
        }
    }
    @Test void cacheOutageFallsBackToSourceAndDoesNotCacheFailures() {
        try(var redis=new RedisStore("redis://127.0.0.1:1",true)) {
            assertThat(redis.cached("currency:rates:USD",Duration.ofHours(1),String.class,()->"source")).isEqualTo("source");
            assertThatThrownBy(()->redis.cached("weather:fail",Duration.ofMinutes(10),String.class,()->{throw new IllegalArgumentException("source failed");}))
                    .hasMessage("source failed");
        }
    }
}
