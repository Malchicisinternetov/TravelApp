package com.example.botservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class BotConfig {
    @Bean
    public BackendClient backendClient(@Value("${bot.users-url}") String users,
                                       @Value("${bot.trips-url}") String trips,
                                       @Value("${bot.expenses-url}") String expenses,
                                       @Value("${bot.service-secret}") String secret) {
        return new BackendClient(client(users, 10), client(trips, 10), client(expenses, 15), secret);
    }

    @Bean
    public TelegramClient telegramClient(@Value("${bot.token}") String token) {
        return new TelegramClient(client("https://api.telegram.org", 35), token);
    }

    private RestClient client(String url, int readSeconds) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }
}
