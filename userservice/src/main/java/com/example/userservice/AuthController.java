package com.example.userservice;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService service;
    private final WebSessionService sessions;

    public AuthController(AuthService service, WebSessionService sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-TravelApp-Client", required = false) String client,
            @CookieValue(value = WebSessionService.COOKIE, required = false) String previous) {
        AuthResponse response = service.register(request);
        var builder = ResponseEntity.created(URI.create("/users/" + response.userId())).cacheControl(CacheControl.noStore());
        if ("web".equals(client)) builder.header(HttpHeaders.SET_COOKIE, sessions.open(response.userId(), previous).toString());
        return builder.body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-TravelApp-Client", required = false) String client,
            @CookieValue(value = WebSessionService.COOKIE, required = false) String previous) {
        AuthResponse response = service.login(request);
        var builder = ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if ("web".equals(client)) builder.header(HttpHeaders.SET_COOKIE, sessions.open(response.userId(), previous).toString());
        return builder.body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader(value = "X-TravelApp-Client", required = false) String client,
            @CookieValue(value = WebSessionService.COOKIE, required = false) String token) {
        sessions.requireWebClient(client);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(sessions.refresh(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-TravelApp-Client", required = false) String client,
            @CookieValue(value = WebSessionService.COOKIE, required = false) String token) {
        sessions.requireWebClient(client);
        sessions.revoke(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, sessions.clearedCookie().toString()).build();
    }
}
