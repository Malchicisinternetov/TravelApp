package com.example.expensesservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TripClientTest {
    @Test
    void readsParticipantsUsingTheOriginalUserToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trip.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://trip.test/trips/10/participants"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andRespond(withSuccess("[{\"userId\":1,\"role\":\"OWNER\"}]", MediaType.APPLICATION_JSON));
        assertThat(new TripClient(builder.build()).participants(10L, "Bearer user-token"))
                .containsExactly(new TripClient.Participant(1L, "OWNER"));
        server.verify();
    }

    @Test
    void propagatesAccessErrorsAndMapsServerFailureToUnavailable() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR}) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://trip.test");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(anything()).andRespond(withStatus(status));
            HttpStatus expected = status.is5xxServerError() ? HttpStatus.SERVICE_UNAVAILABLE : status;
            assertThatThrownBy(() -> new TripClient(builder.build()).participants(10L, "Bearer token"))
                    .isInstanceOfSatisfying(ExpenseException.class, ex -> assertThat(ex.getStatus()).isEqualTo(expected));
            server.verify();
        }
    }

    @Test
    void networkFailureIsUnavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trip.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withException(new IOException("Unavailable")));
        assertThatThrownBy(() -> new TripClient(builder.build()).participants(10L, "Bearer token"))
                .isInstanceOfSatisfying(ExpenseException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
