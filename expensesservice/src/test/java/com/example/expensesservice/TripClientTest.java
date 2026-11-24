package com.example.expensesservice;

import com.example.sharedlib.messaging.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.http.HttpStatus.*;

class TripClientTest {
    @Test void forwardsUserJwtAndReadsParticipants() {
        var rpc = mock(KafkaRpcClient.class);
        when(rpc.call("trips", "GET", "/trips/10/participants", "Bearer token", null, null))
                .thenReturn(JsonMapper.builder().build().readTree("[{\"userId\":1,\"role\":\"OWNER\"}]"));
        assertThat(new TripClient(rpc).participants(10L, "Bearer token"))
                .containsExactly(new TripClient.Participant(1L, "OWNER"));
        verify(rpc).call("trips", "GET", "/trips/10/participants", "Bearer token", null, null);
    }
    @Test void preservesAccessErrorsAndMapsTransportFailureTo503() {
        for (int status : new int[]{401,403,404,500,503}) {
            var rpc = mock(KafkaRpcClient.class);
            when(rpc.call(anyString(), anyString(), anyString(), anyString(), isNull(), isNull()))
                    .thenThrow(new RpcException(status, "private transport details"));
            assertThatThrownBy(() -> new TripClient(rpc).participants(10L,"Bearer token"))
                    .isInstanceOfSatisfying(ExpenseException.class, ex -> assertThat(ex.getStatus().value()).isEqualTo(status >= 500 ? 503 : status));
        }
    }
}
