package com.example.botservice;

import com.example.sharedlib.messaging.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpMethod.*;

class BackendClientTest {
    @Test void serviceSecretIsOnlyUsedForLoginAndJwtForUserOperations() {
        var rpc = mock(KafkaRpcClient.class);
        var json = JsonMapper.builder().build();
        when(rpc.call("users","POST","/internal/auth/telegram",null,"bot-secret",Map.of("telegramId",777L,"name","Alice","create",true)))
                .thenReturn(json.readTree("{\"accessToken\":\"jwt\",\"userId\":42}"));
        when(rpc.call("trips","GET","/trips","Bearer jwt",null,null)).thenReturn(json.readTree("[]"));
        var backend = new BackendClient(rpc,"bot-secret");
        var session = backend.login(777,"Alice",true);
        assertThat(session.userId()).isEqualTo(42);
        assertThat(backend.request("trips",GET,"/trips",session,null).isArray()).isTrue();
        verify(rpc).call("trips","GET","/trips","Bearer jwt",null,null);
    }
    @Test void errorsNeverLeakPayloadOrCredentials() {
        var rpc = mock(KafkaRpcClient.class);
        when(rpc.call("trips","DELETE","/trips/1","Bearer jwt",null,null))
                .thenThrow(new RpcException(403,"private-jwt-and-secret"));
        assertThatThrownBy(() -> new BackendClient(rpc,"secret").request("trips",DELETE,"/trips/1",new BackendClient.Session("jwt",42),null))
                .isInstanceOf(BotException.class).hasMessageContaining("Недостаточно прав").hasMessageNotContaining("jwt").hasMessageNotContaining("secret");
    }
}
