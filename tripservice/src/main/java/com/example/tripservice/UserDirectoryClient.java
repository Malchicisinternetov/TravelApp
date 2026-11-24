package com.example.tripservice;

import com.example.sharedlib.messaging.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import static org.springframework.http.HttpStatus.*;

@Component
public class UserDirectoryClient {
    private final KafkaRpcClient rpc;
    public UserDirectoryClient(KafkaRpcClient rpc) { this.rpc = rpc; }
    public void requireExists(Long userId, String authorization) {
        try {
            var users = rpc.call("users", "POST", "/users/directory", authorization, null, Map.of("userIds", List.of(userId)));
            if (users == null || !users.isArray()) throw new TripException(SERVICE_UNAVAILABLE, "User service returned no response");
            for (var user : users) if (user.path("userId").asLong() == userId) return;
            throw new TripException(NOT_FOUND, "Пользователь с таким ID не найден");
        } catch (RpcException ex) {
            throw new TripException(ex.status() == 401 ? UNAUTHORIZED : SERVICE_UNAVAILABLE,
                    ex.status() == 401 ? "Invalid authentication" : "User service is unavailable");
        }
    }
}
