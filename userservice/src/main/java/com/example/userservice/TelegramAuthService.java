package com.example.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class TelegramAuthService {
    public record LinkCodeResponse(String code, long expiresIn) {}
    private final UserService users;
    private final UserSettingsRepository settings;
    private final TelegramLinkCodeRepository codes;
    private final TokenService tokens;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public TelegramAuthService(UserService users, UserSettingsRepository settings, TelegramLinkCodeRepository codes,
                               TokenService tokens, @Value("${security.bot.secret:}") String secret) {
        if (!secret.isEmpty() && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("BOT_SERVICE_SECRET must contain at least 32 bytes");
        }
        this.users = users;
        this.settings = settings;
        this.codes = codes;
        this.tokens = tokens;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void authenticateBot(String supplied) {
        if (secret.length == 0) {
            throw new AuthException(SERVICE_UNAVAILABLE, "Telegram authentication is disabled");
        }
        if (supplied == null || !MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException(UNAUTHORIZED, "Invalid bot credentials");
        }
    }

    public AuthResponse login(Long telegramId, String name, boolean create) {
        UserSettingsEntity existing = settings.findByTelegramId(telegramId).orElse(null);
        if (existing != null && existing.isTelegramVerified()) {
            return tokens.issue(existing.getUser().getId());
        }
        if (!create) {
            throw new AuthException(NOT_FOUND, "Сначала выполните /start или /link код");
        }
        // Legacy manually entered IDs are not proof of Telegram identity.
        if (existing != null) {
            existing.setTelegramId(null);
            settings.saveAndFlush(existing);
        }
        UserEntity user = new UserEntity();
        user.setName(name);
        users.createUser(user);
        UserSettingsEntity created = users.getUserSettingsByUserId(user.getId());
        created.setTelegramId(telegramId);
        created.setTelegramVerified(true);
        settings.saveAndFlush(created);
        return tokens.issue(user.getId());
    }

    public LinkCodeResponse createCode(Long userId) {
        users.getUserById(userId);
        codes.deleteByExpiresAtBefore(Instant.now());
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        TelegramLinkCode entity = new TelegramLinkCode();
        entity.setTokenHash(hash(code));
        entity.setUserId(userId);
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        codes.save(entity);
        return new LinkCodeResponse(code, 300);
    }

    public AuthResponse link(Long telegramId, String code) {
        TelegramLinkCode link = codes.findForUpdate(hash(code))
                .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new AuthException(BAD_REQUEST, "Код недействителен или истёк"));
        UserSettingsEntity target = users.getUserSettingsByUserId(link.getUserId());
        UserSettingsEntity previous = settings.findByTelegramId(telegramId).orElse(null);
        if (previous != null && previous.isTelegramVerified()
                && !previous.getUser().getId().equals(link.getUserId())) {
            throw new AuthException(CONFLICT, "Telegram уже связан с другим аккаунтом. Для переключения: /link отключить ДА, затем /link код. Сначала сохраните доступ к прежнему аккаунту.");
        }
        if (target.isTelegramVerified() && !telegramId.equals(target.getTelegramId())) {
            throw new AuthException(CONFLICT, "У аккаунта уже есть Telegram. Сначала отключите его.");
        }
        if (previous != null && !previous.getId().equals(target.getId())) {
            previous.setTelegramId(null);
            settings.saveAndFlush(previous);
        }
        target.setTelegramId(telegramId);
        target.setTelegramVerified(true);
        settings.saveAndFlush(target);
        codes.delete(link);
        return tokens.issue(link.getUserId());
    }

    public void unlink(Long userId) {
        UserSettingsEntity target = users.getUserSettingsByUserId(userId);
        target.setTelegramId(null);
        target.setTelegramVerified(false);
        settings.save(target);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
