package dev.cloudpilot.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!";

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), "cloudpilot");
    private final JwtConfig config = new JwtConfig();

    private TokenService tokens;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        tokens = new TokenService(config.jwtEncoder(properties), properties, clock);
        user = new UserAccount("sre@cloudpilot.dev", "Priya SRE", "hash", Role.SRE, NOW);
    }

    private JwtDecoder decoderAt(Instant instant) {
        return config.jwtDecoder(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issuedTokenCarriesUserIdentityAndRole() {
        IssuedToken issued = tokens.issue(user);

        Jwt jwt = decoderAt(NOW).decode(issued.value());

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("sre@cloudpilot.dev");
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Priya SRE");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("SRE");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("cloudpilot");
    }

    @Test
    void tokenExpiresAfterConfiguredTtl() {
        IssuedToken issued = tokens.issue(user);

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(decoderAt(NOW).decode(issued.value()).getExpiresAt()).isEqualTo(issued.expiresAt());
    }

    @Test
    void expiredTokenIsRejected() {
        IssuedToken issued = tokens.issue(user);

        JwtDecoder twoHoursLater = decoderAt(NOW.plus(Duration.ofHours(2)));

        assertThatThrownBy(() -> twoHoursLater.decode(issued.value())).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties other = new JwtProperties("another-secret-that-is-also-32-bytes-long", Duration.ofHours(1), "cloudpilot");
        TokenService attacker = new TokenService(config.jwtEncoder(other), other, Clock.fixed(NOW, ZoneOffset.UTC));

        String forged = attacker.issue(user).value();

        assertThatThrownBy(() -> decoderAt(NOW).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void secretShorterThan32BytesIsRefused() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1), "cloudpilot"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }
}
