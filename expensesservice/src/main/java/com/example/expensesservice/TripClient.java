package com.example.expensesservice;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.util.Arrays;
import java.util.List;
import static org.springframework.http.HttpStatus.*;

@Component
public class TripClient {
    public record Participant(Long userId, String role) {}

    private final RestClient client;

    public TripClient(RestClient tripRestClient) {
        this.client = tripRestClient;
    }

    public List<Participant> participants(Long tripId, String authorization) {
        try {
            Participant[] participants = client.get().uri("/trips/{id}/participants", tripId)
                    .header(HttpHeaders.AUTHORIZATION, authorization).retrieve().body(Participant[].class);
            if (participants == null) {
                throw new ExpenseException(SERVICE_UNAVAILABLE, "Trip service returned no participants");
            }
            return Arrays.asList(participants);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().value() == 401) {
                throw new ExpenseException(UNAUTHORIZED, "Invalid authentication");
            }
            if (ex.getStatusCode().value() == 403) {
                throw new ExpenseException(FORBIDDEN, "User is not a trip participant");
            }
            if (ex.getStatusCode().value() == 404) {
                throw new ExpenseException(NOT_FOUND, "Trip not found: " + tripId);
            }
            throw new ExpenseException(SERVICE_UNAVAILABLE, "Trip service is unavailable");
        } catch (RestClientException ex) {
            throw new ExpenseException(SERVICE_UNAVAILABLE, "Trip service is unavailable");
        }
    }
}
