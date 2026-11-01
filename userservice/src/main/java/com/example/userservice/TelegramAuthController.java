package com.example.userservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
public class TelegramAuthController {
    public record TelegramLogin(@NotNull @Positive Long telegramId,
                                @NotBlank @Size(max = 255) String name, boolean create) {}
    public record TelegramLink(@NotNull @Positive Long telegramId,
                               @NotBlank @Size(max = 100) String code) {}
    private final TelegramAuthService service;

    public TelegramAuthController(TelegramAuthService service) {
        this.service = service;
    }

    @PostMapping("/internal/auth/telegram")
    public ResponseEntity<AuthResponse> login(@RequestHeader(value = "X-Bot-Secret", required = false) String secret,
                                              @Valid @RequestBody TelegramLogin request) {
        service.authenticateBot(secret);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.login(request.telegramId(), request.name(), request.create()));
    }

    @PostMapping("/internal/auth/telegram/link")
    public ResponseEntity<AuthResponse> link(@RequestHeader(value = "X-Bot-Secret", required = false) String secret,
                                             @Valid @RequestBody TelegramLink request) {
        service.authenticateBot(secret);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.link(request.telegramId(), request.code()));
    }

    @PostMapping("/auth/telegram/code")
    public ResponseEntity<TelegramAuthService.LinkCodeResponse> code(Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.createCode(Long.valueOf(principal.getName())));
    }

    @DeleteMapping("/auth/telegram")
    public ResponseEntity<Void> unlink(Principal principal) {
        service.unlink(Long.valueOf(principal.getName()));
        return ResponseEntity.noContent().build();
    }
}
