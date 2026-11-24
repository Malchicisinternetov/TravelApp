package com.example.userservice;

import com.example.sharedlib.messaging.*;
import jakarta.validation.Validator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class UserKafkaEndpoint implements RpcEndpoint {
    private final UserController users;
    private final UserDirectoryController directory;
    private final TelegramAuthController telegram;
    private final NotificationRecipientsController notifications;
    private final RpcSupport auth;
    private final ObjectMapper json;
    private final TelegramAuthService botAuth;
    private final com.example.sharedlib.security.ServiceSecret notificationSecret;
    public UserKafkaEndpoint(UserController users, UserDirectoryController directory, TelegramAuthController telegram,
            NotificationRecipientsController notifications, JwtDecoder decoder, Validator validator, ObjectMapper json,
            TelegramAuthService botAuth, @org.springframework.beans.factory.annotation.Value("${security.notifications.secret:}") String secret) {
        this.users = users; this.directory = directory; this.telegram = telegram; this.notifications = notifications;
        this.auth = new RpcSupport(decoder, validator); this.json = json;
        this.botAuth = botAuth; this.notificationSecret = new com.example.sharedlib.security.ServiceSecret(secret);
    }
    @Override public void authorize(RpcRequest r) {
        try {
            if ("POST".equals(r.method()) && ("/internal/auth/telegram".equals(r.path()) || "/internal/auth/telegram/link".equals(r.path()))) botAuth.authenticateBot(r.serviceSecret());
            else if ("POST".equals(r.method()) && "/internal/notifications/telegram-recipients".equals(r.path())) notificationSecret.verify(r.serviceSecret());
            else auth.jwt(r);
        } catch (AuthException ex) { throw new RpcException(ex.getStatus().value(), ex.getMessage()); }
    }
    @Override public Object handle(RpcRequest r) {
        try { return dispatch(r); }
        catch (AuthException ex) { throw new RpcException(ex.getStatus().value(), ex.getMessage()); }
        catch (UserNotFoundException | UserSettingsNotFoundException ex) { throw new RpcException(404, ex.getMessage()); }
    }
    private Object dispatch(RpcRequest r) {
        if ("POST".equals(r.method())) {
            if ("/internal/auth/telegram".equals(r.path()))
                return telegram.login(r.serviceSecret(), auth.body(r, TelegramAuthController.TelegramLogin.class)).getBody();
            if ("/internal/auth/telegram/link".equals(r.path()))
                return telegram.link(r.serviceSecret(), auth.body(r, TelegramAuthController.TelegramLink.class)).getBody();
            if ("/internal/notifications/telegram-recipients".equals(r.path()))
                return notifications.recipients(r.serviceSecret(), auth.body(r, NotificationRecipientsController.Request.class));
        }
        var principal = auth.principal(r);
        if ("POST".equals(r.method()) && "/users/directory".equals(r.path()))
            return directory.lookup(auth.body(r, UserDirectoryController.Request.class));
        if ("POST".equals(r.method()) && "/auth/telegram/code".equals(r.path())) return telegram.code(principal).getBody();
        if ("DELETE".equals(r.method()) && "/auth/telegram".equals(r.path())) { telegram.unlink(principal); return null; }
        String[] path = r.path().split("/");
        if (path.length < 3 || !"users".equals(path[1])) throw new RpcException(404, "Unknown operation");
        Long id = RpcSupport.id(path[2]);
        if (!principal.getName().equals(id.toString())) throw new RpcException(403, "Access denied");
        if (path.length == 3) return switch (r.method()) {
            case "GET" -> profile(users.getUserById(id).getBody());
            case "PUT" -> profile(users.updateUser(id, profileBody(r)).getBody());
            case "DELETE" -> { users.deleteUser(id); yield null; }
            default -> throw new RpcException(404, "Unknown operation");
        };
        if (path.length == 4 && "settings".equals(path[3])) return switch (r.method()) {
            case "GET" -> users.getUserSettings(id).getBody();
            case "PUT" -> users.updateUserSettings(id, auth.body(r, UpdateUserSettingsRequest.class)).getBody();
            default -> throw new RpcException(404, "Unknown operation");
        };
        throw new RpcException(404, "Unknown operation");
    }
    private UserEntity profileBody(RpcRequest r) {
        // Reuse the Currency serializer/deserializer configured for the REST profile contract.
        try {
            var body = json.treeToValue(r.body(), UserEntity.class);
            if (body == null) throw new RpcException(400, "Invalid request body");
            return body;
        } catch (RuntimeException ex) { throw new RpcException(400, "Invalid request body"); }
    }
    private JsonNode profile(UserEntity user) { return json.valueToTree(user); }
}
