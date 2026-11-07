package com.example.sharedlib.security;

import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.springframework.http.HttpStatus.*;

public final class ServiceSecret {
    private final byte[] secret;

    public ServiceSecret(String configured) {
        secret = configured.getBytes(StandardCharsets.UTF_8);
        if (secret.length > 0 && secret.length < 32) {
            throw new IllegalArgumentException("NOTIFICATION_SERVICE_SECRET must contain at least 32 bytes");
        }
    }

    public void verify(String supplied) {
        if (secret.length == 0) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Notifications are disabled");
        if (supplied == null || !MessageDigest.isEqual(secret, supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid service credentials");
        }
    }
}
