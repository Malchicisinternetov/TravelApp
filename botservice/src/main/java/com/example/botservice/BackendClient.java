package com.example.botservice;

import org.springframework.http.HttpMethod;
import org.springframework.web.client.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;

public class BackendClient {
    public record Session(String accessToken, long userId) {}
    private final RestClient users;
    private final RestClient trips;
    private final RestClient expenses;
    private final String secret;

    public BackendClient(RestClient users, RestClient trips, RestClient expenses, String secret) {
        this.users = users;
        this.trips = trips;
        this.expenses = expenses;
        this.secret = secret;
    }

    public Session login(long telegramId, String name, boolean create) {
        return session(internal("/internal/auth/telegram",
                Map.of("telegramId", telegramId, "name", name, "create", create)));
    }

    public Session link(long telegramId, String code) {
        return session(internal("/internal/auth/telegram/link", Map.of("telegramId", telegramId, "code", code)));
    }

    public JsonNode request(String service, HttpMethod method, String path, Session session, Object body) {
        RestClient client = switch (service) {
            case "users" -> users;
            case "trips" -> trips;
            case "expenses" -> expenses;
            default -> throw new IllegalArgumentException("Unknown service");
        };
        return execute(client.method(method).uri(path).headers(h -> h.setBearerAuth(session.accessToken())), body);
    }

    private JsonNode internal(String path, Object body) {
        return execute(users.post().uri(path).header("X-Bot-Secret", secret), body);
    }

    private JsonNode execute(RestClient.RequestBodySpec request, Object body) {
        try {
            if (body != null) {
                request.body(body);
            }
            return request.retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException ex) {
            String message = switch (ex.getStatusCode().value()) {
                case 401 -> "Не удалось выполнить вход. Проверьте подключение аккаунта.";
                case 403 -> "Недостаточно прав для этой операции.";
                case 404 -> "Объект не найден. Для начала работы выполните /start.";
                case 409 -> "Операция конфликтует с текущими данными.";
                default -> "Проверьте аргументы команды.";
            };
            try {
                JsonNode error = JsonMapper.builder().build().readTree(ex.getResponseBodyAsString());
                if (error.hasNonNull("message")) message += "\n" + error.get("message").asText();
            } catch (Exception ignored) {
                // Do not display raw HTTP bodies, headers or tokens.
            }
            throw new BotException(message);
        } catch (RestClientException ex) {
            throw new BotException("Сервис временно недоступен. Попробуйте позже.");
        }
    }

    private Session session(JsonNode response) {
        if (response == null || !response.hasNonNull("accessToken") || !response.hasNonNull("userId")) {
            throw new BotException("Некорректный ответ сервиса входа.");
        }
        return new Session(response.get("accessToken").asText(), response.get("userId").asLong());
    }
}
