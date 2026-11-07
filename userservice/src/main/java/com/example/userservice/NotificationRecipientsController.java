package com.example.userservice;

import com.example.sharedlib.notifications.NotificationContracts.Recipient;
import com.example.sharedlib.security.ServiceSecret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class NotificationRecipientsController {
    public record Request(@NotEmpty @Size(max = 500) List<@NotNull @Positive Long> userIds) {}
    private final UserSettingsRepository settings;
    private final ServiceSecret secret;

    public NotificationRecipientsController(UserSettingsRepository settings,
                                             @Value("${security.notifications.secret:}") String secret) {
        this.settings = settings;
        this.secret = new ServiceSecret(secret);
    }

    @PostMapping("/internal/notifications/telegram-recipients")
    @Transactional(readOnly = true)
    public List<Recipient> recipients(@RequestHeader(value = "X-Notification-Secret", required = false) String supplied,
                                      @Valid @RequestBody Request request) {
        secret.verify(supplied);
        return settings.findByUserIdInAndTelegramVerifiedTrueAndTelegramIdIsNotNull(request.userIds()).stream()
                .filter(s -> s.getNotificationMethod() == null
                        || s.getNotificationMethod().isBlank()
                        || "TELEGRAM".equalsIgnoreCase(s.getNotificationMethod()))
                .map(s -> new Recipient(s.getUser().getId(), s.getTelegramId())).toList();
    }
}
