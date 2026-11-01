package com.example.botservice;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramClientTest {
    @Test
    void pollingUsesOffsetAndRepliesAreSplitWithoutBreakingEmoji() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://telegram.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bottoken/getUpdates"))
                .andExpect(jsonPath("$.offset").value(123)).andExpect(jsonPath("$.timeout").value(25))
                .andRespond(withSuccess("{\"ok\":true,\"result\":[]}", MediaType.APPLICATION_JSON));
        String emoji = new String(Character.toChars(0x1F30D));
        server.expect(requestTo("https://telegram.test/bottoken/sendMessage"))
                .andExpect(jsonPath("$.text").value(emoji.repeat(1750))).andRespond(withSuccess());
        server.expect(requestTo("https://telegram.test/bottoken/sendMessage"))
                .andExpect(jsonPath("$.text").value(emoji.repeat(250))).andRespond(withSuccess());
        TelegramClient client = new TelegramClient(builder.build(), "token");
        client.updates(123);
        client.send(777, emoji.repeat(2000));
        server.verify();
    }

    @Test
    void onlyPrivateMessagesUseSenderIdentity() throws Exception {
        TelegramClient telegram = mock(TelegramClient.class);
        BotCommands commands = mock(BotCommands.class);
        when(commands.execute(777, "Alice", "/me")).thenReturn("Profile");
        TelegramPolling polling = new TelegramPolling(telegram, commands, "token", "a".repeat(32), "target/test-offset.txt");
        var mapper = JsonMapper.builder().build();
        polling.handle(mapper.readTree("""
                {"message":{"chat":{"id":999,"type":"private"},"from":{"id":777,"first_name":"Alice"},"text":"/me"}}
                """));
        verify(commands).execute(777, "Alice", "/me");
        verify(telegram).send(999, "Profile");
        polling.handle(mapper.readTree("""
                {"message":{"chat":{"id":-10,"type":"group"},"from":{"id":888},"text":"/me"}}
                """));
        verifyNoMoreInteractions(commands);
    }
}
