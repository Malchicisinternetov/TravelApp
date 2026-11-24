package com.example.botservice;

import com.example.sharedlib.messaging.*;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;
import java.util.Map;

public class BackendClient {
    public record Session(String accessToken, long userId) {}
    private final KafkaRpcClient rpc;
    private final String secret;
    public BackendClient(KafkaRpcClient rpc, String secret) { this.rpc = rpc; this.secret = secret; }
    public Session login(long telegramId, String name, boolean create) {
        return session(execute("users", "POST", "/internal/auth/telegram", null, secret,
                Map.of("telegramId", telegramId, "name", name, "create", create)));
    }
    public Session link(long telegramId, String code) {
        return session(execute("users", "POST", "/internal/auth/telegram/link", null, secret,
                Map.of("telegramId", telegramId, "code", code)));
    }
    public JsonNode request(String service, HttpMethod method, String path, Session session, Object body) {
        return execute(service, method.name(), path, "Bearer " + session.accessToken(), null, body);
    }
    private JsonNode execute(String service, String method, String path, String authorization, String credential, Object body) {
        try { return rpc.call(service, method, path, authorization, credential, body); }
        catch (RpcException ex) {
            String message = switch (ex.status()) {
                case 401 -> "Не удалось выполнить вход. Проверьте подключение аккаунта.";
                case 403 -> "Недостаточно прав для этой операции.";
                case 404 -> "Объект не найден. Для начала работы выполните /start.";
                case 409 -> "Операция конфликтует с текущими данными.";
                case 503 -> "Ответ сервиса не получен. Проверьте результат командой просмотра, прежде чем повторять изменение.";
                default -> "Проверьте аргументы команды.";
            };
            throw new BotException(message);
        }
    }
    private Session session(JsonNode response) {
        if (response == null || !response.hasNonNull("accessToken") || !response.hasNonNull("userId"))
            throw new BotException("Некорректный ответ сервиса входа.");
        return new Session(response.get("accessToken").asText(), response.get("userId").asLong());
    }
}
