package dev.cloudpilot.auth;

import java.time.Instant;
import java.util.UUID;

public record LoginResult(String accessToken, String tokenType, Instant expiresAt, UserSummary user) {

    public LoginResult(String accessToken, Instant expiresAt, UserSummary user) {
        this(accessToken, "Bearer", expiresAt, user);
    }

    public record UserSummary(UUID id, String email, String displayName, Role role) {

        static UserSummary of(UserAccount user) {
            return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
        }
    }
}
