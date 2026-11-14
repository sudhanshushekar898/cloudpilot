package dev.cloudpilot.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings from application.yml (cloudpilot.security.jwt.*).
 *
 * @param secret   HMAC-SHA256 signing key; must be at least 32 bytes (256 bits)
 * @param ttl      how long an access token is valid
 * @param issuer   value of the "iss" claim, checked when decoding
 */
@ConfigurationProperties(prefix = "cloudpilot.security.jwt")
public record JwtProperties(String secret, Duration ttl, String issuer) {

    /** Shipped only for local development; refused when the prod profile is active. */
    public static final String DEV_SECRET = "dev-only-insecure-jwt-secret-change-me-0123456789";

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "cloudpilot.security.jwt.secret must be at least 32 bytes for HS256");
        }
        if (ttl == null) {
            ttl = Duration.ofHours(1);
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "cloudpilot";
        }
    }
}
