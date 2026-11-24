package com.example.sharedlib.messaging;

import com.example.sharedlib.redis.RedisStore;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import java.time.Duration;
import java.util.*;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@Import(RedisStore.Config.class)
public class KafkaRpcConfiguration {
    @Bean
    public DefaultKafkaProducerFactory<String, String> travelKafkaProducerFactory(
            @Value("${travel.kafka.bootstrap-servers:localhost:9092}") String brokers,
            @Value("${travel.kafka.security-protocol:PLAINTEXT}") String protocol,
            @Value("${travel.kafka.sasl-mechanism:PLAIN}") String mechanism,
            @Value("${travel.kafka.sasl-jaas-config:}") String jaas) {
        Map<String, Object> config = connection(brokers, protocol, mechanism, jaas);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10000);
        return new DefaultKafkaProducerFactory<>(config);
    }
    @Bean
    public KafkaTemplate<String, String> travelKafkaTemplate(DefaultKafkaProducerFactory<String, String> travelKafkaProducerFactory) {
        return new KafkaTemplate<>(travelKafkaProducerFactory);
    }
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> travelKafkaListenerFactory(
            @Value("${travel.kafka.bootstrap-servers:localhost:9092}") String brokers,
            @Value("${travel.kafka.enabled:true}") boolean enabled,
            @Value("${travel.kafka.security-protocol:PLAINTEXT}") String protocol,
            @Value("${travel.kafka.sasl-mechanism:PLAIN}") String mechanism,
            @Value("${travel.kafka.sasl-jaas-config:}") String jaas) {
        Map<String, Object> config = connection(brokers, protocol, mechanism, jaas);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 180000);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setAutoStartup(enabled);
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler((record, error) ->
                org.slf4j.LoggerFactory.getLogger(KafkaRpcConfiguration.class).warn("Kafka reply failed; topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset()), new org.springframework.util.backoff.FixedBackOff(1000, 2)));
        return factory;
    }
    @Bean
    public KafkaRpcClient kafkaRpcClient(KafkaTemplate<String, String> travelKafkaTemplate,
            @Value("${travel.kafka.timeout-seconds:30}") long seconds,
            @Value("${travel.kafka.enabled:true}") boolean enabled) {
        if (seconds < 1 || seconds > 90) throw new IllegalArgumentException("Kafka timeout must be 1..90 seconds");
        return new KafkaRpcClient(travelKafkaTemplate, Duration.ofSeconds(seconds), enabled);
    }
    @Bean
    @ConditionalOnBean(RpcEndpoint.class)
    public KafkaRpcServer kafkaRpcServer(RpcEndpoint endpoint, RedisStore redis, KafkaTemplate<String, String> travelKafkaTemplate,
                                         @Value("${spring.application.name}") String service) {
        return new KafkaRpcServer(endpoint, redis, travelKafkaTemplate, service);
    }
    @Bean
    public KafkaAdmin travelKafkaAdmin(@Value("${travel.kafka.bootstrap-servers:localhost:9092}") String brokers,
            @Value("${travel.kafka.enabled:true}") boolean enabled,
            @Value("${travel.kafka.auto-create-topics:true}") boolean autoCreate,
            @Value("${travel.kafka.security-protocol:PLAINTEXT}") String protocol,
            @Value("${travel.kafka.sasl-mechanism:PLAIN}") String mechanism,
            @Value("${travel.kafka.sasl-jaas-config:}") String jaas) {
        var admin = new KafkaAdmin(connection(brokers, protocol, mechanism, jaas));
        admin.setAutoCreate(enabled && autoCreate);
        admin.setOperationTimeout(5);
        return admin;
    }
    @Bean
    public KafkaAdmin.NewTopics travelRpcTopics() {
        return new KafkaAdmin.NewTopics(topic("travel.rpc.users"), topic("travel.rpc.trips"),
                topic("travel.rpc.expenses"), topic("travel.rpc.replies"));
    }
    private NewTopic topic(String name) {
        return new NewTopic(name, 1, (short) 1).configs(Map.of("retention.ms", "300000", "segment.ms", "60000", "cleanup.policy", "delete"));
    }
    private Map<String, Object> connection(String brokers, String protocol, String mechanism, String jaas) {
        Map<String, Object> config = new HashMap<>();
        config.put("bootstrap.servers", brokers); config.put("security.protocol", protocol);
        if (!jaas.isBlank()) { config.put("sasl.mechanism", mechanism); config.put("sasl.jaas.config", jaas); }
        return config;
    }
}
