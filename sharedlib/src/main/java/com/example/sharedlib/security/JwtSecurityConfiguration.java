package com.example.sharedlib.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class JwtSecurityConfiguration {
    public static final String AUDIENCE = "travelapp-api";

    @Bean
    public SecretKey jwtSigningKey(@Value("${security.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey,
                                 @Value("${security.jwt.issuer:travelapp}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer), jwt -> {
                    if (validSubject(jwt.getSubject()) && jwt.getExpiresAt() != null
                            && jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)) {
                        return OAuth2TokenValidatorResult.success();
                    }
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error("invalid_token", "Invalid token claims", null));
                }));
        return decoder;
    }

    private static boolean validSubject(String subject) {
        try {
            return subject != null && Long.parseLong(subject) > 0
                    && Long.toString(Long.parseLong(subject)).equals(subject);
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
