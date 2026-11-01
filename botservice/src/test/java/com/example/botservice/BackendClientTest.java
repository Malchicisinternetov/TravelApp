package com.example.botservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.http.HttpMethod.*;

class BackendClientTest {
    @Test
    void botSecretIsUsedOnlyForLoginAndUserJwtForServiceCalls() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://backend.test/internal/auth/telegram"))
                .andExpect(header("X-Bot-Secret", "bot-secret"))
                .andExpect(jsonPath("$.telegramId").value(777))
                .andRespond(withSuccess("{\"accessToken\":\"user-jwt\",\"userId\":42}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://backend.test/trips"))
                .andExpect(header("Authorization", "Bearer user-jwt"))
                .andExpect(headerDoesNotExist("X-Bot-Secret"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        BackendClient backend = new BackendClient(client, client, client, "bot-secret");
        var session = backend.login(777, "Alice", true);
        assertThat(session.userId()).isEqualTo(42);
        assertThat(backend.request("trips", GET, "/trips", session, null).isArray()).isTrue();
        server.verify();
    }

    @Test
    void backendErrorsAreReturnedWithoutRawHttpDetails() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://backend.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                .body("{\"message\":\"Only owners can delete trips\"}").contentType(MediaType.APPLICATION_JSON));
        RestClient client = builder.build();
        BackendClient backend = new BackendClient(client, client, client, "bot-secret");
        assertThatThrownBy(() -> backend.request("trips", DELETE, "/trips/1",
                new BackendClient.Session("user-jwt", 42), null))
                .isInstanceOf(BotException.class).hasMessageContaining("Недостаточно прав")
                .hasMessageNotContaining("user-jwt").hasMessageNotContaining("bot-secret");
    }
}
