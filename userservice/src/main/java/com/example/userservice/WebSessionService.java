package com.example.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.Base64;
import java.util.HexFormat;
import static org.springframework.http.HttpStatus.*;

@Service
public class WebSessionService {
    public static final String COOKIE = "travelapp_refresh";
    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final TokenService tokens;
    private final boolean secure;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public WebSessionService(RefreshSessionRepository sessions, UserRepository users, TokenService tokens,
            @Value("${security.web-session.secure:false}") boolean secure,
            @Value("${security.web-session.ttl-seconds:604800}") long seconds) {
        if (seconds < 3600 || seconds > 2592000) throw new IllegalArgumentException("Web session lifetime must be 1 hour to 30 days");
        this.sessions = sessions; this.users = users; this.tokens = tokens;
        this.secure = secure; this.lifetime = Duration.ofSeconds(seconds);
    }

    @Transactional
    public ResponseCookie open(Long userId, String previous) {
        revoke(previous);
        sessions.deleteByExpiresAtBefore(Instant.now());
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSession session = new RefreshSession();
        session.setTokenHash(hash(token)); session.setUserId(userId);
        session.setExpiresAt(Instant.now().plus(lifetime)); sessions.save(session);
        return cookie(token, lifetime);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String token) {
        if (token == null || token.length() != 43) throw new AuthException(UNAUTHORIZED, "Web session expired");
        RefreshSession session = sessions.findById(hash(token))
                .filter(s -> s.getExpiresAt().isAfter(Instant.now()) && users.existsById(s.getUserId()))
                .orElseThrow(() -> new AuthException(UNAUTHORIZED, "Web session expired"));
        // Keep the opaque credential stable to allow parallel browser tabs to refresh safely.
        // Its absolute expiry is never extended by refreshing an access token.
        return tokens.issue(session.getUserId());
    }

    @Transactional
    public void revoke(String token) {
        if (token != null && token.length() == 43) sessions.deleteById(hash(token));
    }

    public ResponseCookie clearedCookie() { return cookie("", Duration.ZERO); }

    public void requireWebClient(String client) {
        if (!"web".equals(client)) throw new AuthException(FORBIDDEN, "Web client header required");
    }

    private ResponseCookie cookie(String token, Duration age) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secure).sameSite("Strict")
                .path("/api/users/auth").maxAge(age).build();
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
