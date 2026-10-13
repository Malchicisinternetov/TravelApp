package com.example.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserServiceApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void contextLoads() throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String baseUrl = "http://localhost:" + port;

        // 1. Создание пользователя (POST)
        User user = new User(null, ZoneId.of("UTC+5"), Currency.getInstance("RUB"), BigDecimal.ZERO, "ilyusha");
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(user);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        User user1 = objectMapper.readValue(response.body(), User.class);

        assertNotNull(user1, "Пользователь не должен быть null");
        assertEquals(user.currency(), user1.currency(), "Валюта должна совпадать");
        assertEquals(user.name(), user1.name(), "Имя должно совпадать");
        assertEquals(user.timezone(), user1.timezone(), "Часовой пояс должен совпадать");
        assertEquals(0, user1.amount().compareTo(user.amount()), "Сумма должна совпадать");

        // 2. Получение пользователя (GET)
        HttpRequest request2 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/1"))
                .GET()
                .build();
        HttpResponse<InputStream> response2 = client.send(request2, HttpResponse.BodyHandlers.ofInputStream());

        User user2 = objectMapper.readValue(response2.body(), User.class);

        assertEquals(0, user2.amount().compareTo(user1.amount()), "Сумма должна совпадать");
        assertEquals(user2.name(), user1.name(), "Имя должно совпадать");
        assertEquals(user2.timezone(), user1.timezone(), "Часовой пояс должен совпадать");
        assertEquals(user2.currency(), user1.currency(), "Валюта должна совпадать");

        // 3. Удаление пользователя (DELETE)
        HttpRequest request3 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + user1.id()))
                .DELETE()
                .build();
        client.send(request3, HttpResponse.BodyHandlers.ofString());

        // 4. Обновление пользователя (PUT)
        HttpRequest request4 = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/" + user1.id()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response4 = client.send(request4, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response4.statusCode(), "Код ответа должен быть 404");
    }
}