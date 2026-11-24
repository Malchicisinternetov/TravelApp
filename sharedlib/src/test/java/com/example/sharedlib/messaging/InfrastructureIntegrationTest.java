package com.example.sharedlib.messaging;

import com.example.sharedlib.redis.RedisStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Optional smoke test against isolated, running users/trips/expenses services and real Kafka/Redis. */
@EnabledIfSystemProperty(named="travel.integration", matches="true")
@org.junit.jupiter.api.Tag("integration")
class InfrastructureIntegrationTest {
    @Test void sharedCacheAndServiceChainWorkAcrossRealProcesses() throws Exception {
        String redisUrl = System.getenv("TRAVEL_QA_REDIS_URL");
        var counter = new AtomicInteger();
        String key="probe:"+UUID.randomUUID();
        try(var first=new RedisStore(redisUrl,true); var second=new RedisStore(redisUrl,true)) {
            assertThat(first.cached(key,Duration.ofSeconds(1),String.class,()->"source-"+counter.incrementAndGet())).isEqualTo("source-1");
            assertThat(second.cached(key,Duration.ofSeconds(1),String.class,()->"source-"+counter.incrementAndGet())).isEqualTo("source-1");
            Thread.sleep(1100);
            assertThat(second.cached(key,Duration.ofSeconds(1),String.class,()->"source-"+counter.incrementAndGet())).isEqualTo("source-2");
        }
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("probe",Map.of(
                    "spring.application.name","infrastructure-probe",
                    "travel.kafka.bootstrap-servers",System.getenv("TRAVEL_QA_KAFKA_BROKERS"),
                    "travel.kafka.enabled","true", "travel.redis.url",redisUrl, "random.uuid",UUID.randomUUID().toString())));
            context.register(KafkaRpcConfiguration.class); context.refresh();
            var rpc=context.getBean(KafkaRpcClient.class);
            String owner="Bearer "+System.getenv("TRAVEL_QA_OWNER_JWT");
            String member="Bearer "+System.getenv("TRAVEL_QA_MEMBER_JWT");
            long memberId=Long.parseLong(System.getenv("TRAVEL_QA_MEMBER_ID"));
            long ownerId=Long.parseLong(System.getenv("TRAVEL_QA_OWNER_ID"));
            var profile=rpc.call("users","PUT","/users/"+ownerId,owner,null,Map.of("name","Kafka Owner","currency","USD","timezone","Europe/Moscow","amount","1000.10"));
            assertThat(profile.path("currency").asText()).isEqualTo("USD");
            assertThat(profile.has("username")).isFalse();
            assertThat(profile.has("passwordHash")).isFalse();
            var trip=rpc.call("trips","POST","/trips",owner,null,Map.of("name","Kafka journey","destinationPoint","Paris","legs",List.of()));
            long tripId=trip.path("id").asLong();
            assertThat(tripId).isPositive();
            var updated=rpc.call("trips","POST","/trips/"+tripId+"/participants",owner,null,Map.of("userId",memberId,"role","READ_ONLY"));
            assertThat(updated.path("participants").size()).isEqualTo(2);
            var expense=rpc.call("expenses","POST","/trips/"+tripId+"/expenses",owner,null,Map.of("description","Tickets","amount","100.00","currency","USD"));
            assertThat(expense.path("shares").size()).isEqualTo(2);
            assertThat(rpc.call("expenses","GET","/trips/"+tripId+"/expenses/summary",owner,null,null).size()).isEqualTo(1);
            assertThatThrownBy(()->rpc.call("expenses","POST","/trips/"+tripId+"/expenses",member,null,Map.of("description","Denied","amount","10","currency","USD")))
                    .isInstanceOfSatisfying(RpcException.class,ex->assertThat(ex.status()).isEqualTo(403));
            assertThatThrownBy(()->rpc.call("trips","GET","/trips/"+tripId,"Bearer invalid",null,null))
                    .isInstanceOfSatisfying(RpcException.class,ex->assertThat(ex.status()).isEqualTo(401));
            assertThatThrownBy(()->rpc.call("users","GET","/users/"+memberId,owner,null,null))
                    .isInstanceOfSatisfying(RpcException.class,ex->assertThat(ex.status()).isEqualTo(403));
            String secret=System.getenv("TRAVEL_QA_NOTIFICATION_SECRET");
            assertThat(rpc.call("trips","GET","/internal/trips/departures",null,secret,
                    Map.of("from",Instant.now(),"to",Instant.now().plusSeconds(10800))).isArray()).isTrue();
            assertThat(rpc.call("users","POST","/internal/notifications/telegram-recipients",null,secret,Map.of("userIds",List.of(memberId))).isArray()).isTrue();
            var json=JsonMapper.builder().build();
            String name="deduplicated-"+UUID.randomUUID();
            var command=new RpcRequest(UUID.randomUUID().toString(),System.currentTimeMillis()+30000,"POST","/trips",owner,null,
                    json.valueToTree(Map.of("name",name,"destinationPoint","Rome","legs",List.of())));
            KafkaTemplate<String,String> kafka=context.getBean(KafkaTemplate.class);
            for(int i=0;i<2;i++) kafka.send("travel.rpc.trips",command.id(),json.writeValueAsString(command)).get(10,TimeUnit.SECONDS);
            var trips=rpc.call("trips","GET","/trips",owner,null,null);
            int matches=0; for(var item:trips) if(name.equals(item.path("name").asText())) matches++;
            assertThat(matches).isEqualTo(1);
        }
    }
}
