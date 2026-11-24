package com.example.expensesservice;

import com.example.sharedlib.messaging.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Component
public class TripClient {
    public record Participant(Long userId, String role) {}
    private final KafkaRpcClient rpc;
    public TripClient(KafkaRpcClient rpc) { this.rpc = rpc; }
    public List<Participant> participants(Long tripId, String authorization) {
        try {
            var response = rpc.call("trips", "GET", "/trips/" + tripId + "/participants", authorization, null, null);
            if (response == null || !response.isArray()) throw new ExpenseException(SERVICE_UNAVAILABLE, "Trip service returned no participants");
            return Arrays.asList(JsonMapper.builder().build().treeToValue(response, Participant[].class));
        } catch (RpcException ex) {
            var status = switch (ex.status()) { case 401 -> UNAUTHORIZED; case 403 -> FORBIDDEN; case 404 -> NOT_FOUND; default -> SERVICE_UNAVAILABLE; };
            throw new ExpenseException(status, switch (ex.status()) { case 401 -> "Invalid authentication"; case 403 -> "User is not a trip participant"; case 404 -> "Trip not found: " + tripId; default -> "Trip service is unavailable"; });
        }
    }
}
