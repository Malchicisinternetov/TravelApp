package com.example.tripservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import static org.springframework.http.HttpStatus.*;

@Component
public class UserDirectoryClient {
    public record UserSummary(Long userId, String name) {}
    private final RestClient client;

    public UserDirectoryClient(@Value("${services.user.url:http://localhost:8082}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public void requireExists(Long userId, String authorization) {
        try {
            UserSummary[] users = client.post().uri("/users/directory").header(HttpHeaders.AUTHORIZATION, authorization)
                    .body(Map.of("userIds", List.of(userId))).retrieve().body(UserSummary[].class);
            if (users == null) throw new TripException(SERVICE_UNAVAILABLE, "User service returned no response");
            if (java.util.Arrays.stream(users).noneMatch(user -> userId.equals(user.userId()))) {
                throw new TripException(NOT_FOUND, "Пользователь с таким ID не найден");
            }
        } catch (HttpClientErrorException.Unauthorized ex) {
            throw new TripException(UNAUTHORIZED, "Invalid authentication");
        } catch (RestClientException ex) {
            throw new TripException(SERVICE_UNAVAILABLE, "User service is unavailable");
        }
    }
}
