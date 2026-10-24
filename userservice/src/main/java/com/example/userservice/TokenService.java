package com.example.userservice;

import com.example.sharedlib.security.JwtSecurityConfiguration;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final long ttlSeconds;

    public TokenService(SecretKey jwtSigningKey, @Value("${security.jwt.issuer:travelapp}") String issuer,
                        @Value("${security.jwt.ttl-seconds:900}") long ttlSeconds) {
        if (ttlSeconds < 60 || ttlSeconds > 86400) {
            throw new IllegalArgumentException("JWT_TTL_SECONDS must be between 60 and 86400");
        }
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    public AuthResponse issue(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer)
                .subject(userId.toString()).audience(List.of(JwtSecurityConfiguration.AUDIENCE))
                .issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(ttlSeconds))
                .id(UUID.randomUUID().toString()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", ttlSeconds, userId);
    }
}
