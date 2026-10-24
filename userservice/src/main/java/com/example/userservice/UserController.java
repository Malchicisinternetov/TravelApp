package com.example.userservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }
    @GetMapping("/{id}")
    public ResponseEntity<UserEntity> getUserById(@PathVariable Long id) {
        log.info("getUserById: {}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserEntity> updateUser(@PathVariable Long id, @RequestBody UserEntity user) {
        log.info("updateUser, id: {}", id);
        return ResponseEntity.ok(userService.updateUser(id, user));
    }

    @PostMapping
    public ResponseEntity<UserEntity> createUser(@RequestBody UserEntity user) {
        log.info("createUser");
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.createUser(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("deleteUser, id: {}", id);
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/settings")
    public ResponseEntity<UserSettingsResponse> getUserSettings(@PathVariable Long userId) {
        log.info("getUserSettings, userId: {}", userId);
        var settings = userService.getUserSettingsByUserId(userId);
        return ResponseEntity.ok(UserSettingsResponse.from(settings));
    }

    @PutMapping("/{userId}/settings")
    public ResponseEntity<UserSettingsResponse> updateUserSettings(
            @PathVariable Long userId,
            @RequestBody UpdateUserSettingsRequest request) {
        log.info("updateUserSettings, userId: {}", userId);
        var updated = userService.updateUserSettings(userId, request.toEntity());
        return ResponseEntity.ok(UserSettingsResponse.from(updated));
    }

    @PostMapping("/{userId}/settings/telegram")
    public ResponseEntity<UserSettingsResponse> bindTelegram(
            @PathVariable Long userId,
            @RequestBody BindTelegramRequest request) {
        log.info("bindTelegram, userId: {}, telegramId: {}", userId, request.telegramId());
        var updated = userService.bindTelegram(userId, request.telegramId());
        return ResponseEntity.ok(UserSettingsResponse.from(updated));
    }

    @GetMapping("/{userId}/settings/telegram")
    public ResponseEntity<TelegramIdResponse> getTelegramIdByUserId(@PathVariable Long userId) {
        log.info("getTelegramIdByUserId, userId: {}", userId);
        return ResponseEntity.ok(new TelegramIdResponse(userService.getTelegramIdByUserId(userId)));
    }

    @GetMapping("/by-telegram/{telegramId}")
    public ResponseEntity<UserEntity> getUserByTelegramId(@PathVariable Long telegramId, Principal principal) {
        log.info("getUserByTelegramId, telegramId: {}", telegramId);
        UserEntity user = userService.getUserByTelegramId(telegramId);
        if (!user.getId().toString().equals(principal.getName())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return ResponseEntity.ok(user);
    }
}
