package dev.cloudpilot.auth;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
