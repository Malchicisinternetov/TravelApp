package com.example.notificationservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class NotificationConfig {
    @Bean
    public Clock notificationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public NotificationApiClient notificationApiClient(com.example.sharedlib.messaging.KafkaRpcClient rpc,
            @Value("${notifications.secret}") String secret) {
        return new NotificationApiClient(rpc, secret);
    }
    @Bean
    public TelegramSender telegramSender(@Value("${notifications.bot-token}") String token) {
        return new TelegramSender(client("https://api.telegram.org"), token);
    }

    private RestClient client(String url) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
