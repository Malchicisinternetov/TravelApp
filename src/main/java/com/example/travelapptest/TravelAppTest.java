package com.example.travelapptest;

import com.example.travelapp.User;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Currency;
import java.util.Objects;

public class TravelAppTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        
        User user = new User(null, ZoneId.of("UTC+5"), Currency.getInstance("RUB"), BigDecimal.ZERO, "ilyusha");
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonBody = objectMapper.writeValueAsString(user); 
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        
        //System.out.println(response.body());
        System.out.println(response.statusCode());
        
        User user1 = objectMapper.readValue(response.body(), User.class);

        assert user1 != null;
        assert user1.currency().equals(user.currency());
        assert user1.name().equals(user.name());
        assert user1.timezone().equals(user.timezone());
        assert user1.amount().equals(user.amount());

        HttpRequest request2 = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/id=1")).GET().build();
        HttpResponse<InputStream> response2 = client.send(request2, HttpResponse.BodyHandlers.ofInputStream());
        
        User user2 = objectMapper.readValue(response2.body(), User.class);
        
        assert Objects.equals(user2.amount(), user1.amount());
        assert user2.name().equals(user1.name());
        assert user2.timezone().equals(user1.timezone());
        assert user2.currency().equals(user1.currency());
        
        HttpRequest request3 = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/id="+user1.id())).DELETE().build();
        client.send(request3, HttpResponse.BodyHandlers.ofString());
        
        HttpRequest request4 = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/")).header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(jsonBody)).build();
        HttpResponse<String> response4 = client.send(request4, HttpResponse.BodyHandlers.ofString());

        assert response4.statusCode() == 404; 
        
    }
}
