package com.example.sharedlib.messaging;

import jakarta.validation.Validator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import tools.jackson.databind.json.JsonMapper;
import java.security.Principal;

/** The receiving service validates credentials and payload, not the caller-supplied user id. */
public class RpcSupport {
    private final JwtDecoder decoder;
    private final Validator validator;
    private final JsonMapper json = JsonMapper.builder().build();
    public RpcSupport(JwtDecoder decoder, Validator validator) { this.decoder = decoder; this.validator = validator; }
    public Jwt jwt(RpcRequest request) {
        String value = request.authorization();
        if (value == null || !value.startsWith("Bearer ")) throw new RpcException(401, "Invalid authentication");
        try { return decoder.decode(value.substring(7)); }
        catch (JwtException ex) { throw new RpcException(401, "Invalid authentication"); }
    }
    public Principal principal(RpcRequest request) { String id = jwt(request).getSubject(); return () -> id; }
    public <T> T body(RpcRequest request, Class<T> type) {
        try {
            T body = json.treeToValue(request.body(), type);
            if (body == null || !validator.validate(body).isEmpty()) throw new RpcException(400, "Invalid request body");
            return body;
        } catch (RuntimeException ex) { throw new RpcException(400, "Invalid request body"); }
    }
    public static Long id(String value) {
        try { long id = Long.parseLong(value); if (id > 0) return id; }
        catch (NumberFormatException ignored) { }
        throw new RpcException(400, "Invalid identifier");
    }
}
