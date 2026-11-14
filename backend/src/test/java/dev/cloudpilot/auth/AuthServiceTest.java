package dev.cloudpilot.auth;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

    // Strength 4 keeps tests fast; production uses the default (10).
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Mock
    private UserAccountRepository users;

    @Mock
    private TokenService tokens;

    private AuthService authService;
    private UserAccount sre;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, passwordEncoder, tokens);
        sre = new UserAccount("sre@cloudpilot.dev", "Priya SRE", passwordEncoder.encode("correct-horse"), Role.SRE, NOW);
    }

    @Test
    void validCredentialsReturnTokenAndUserSummary() {
        IssuedToken token = new IssuedToken("jwt-value", NOW.plusSeconds(3600));
        when(users.findByEmailIgnoreCase("sre@cloudpilot.dev")).thenReturn(Optional.of(sre));
        when(tokens.issue(sre)).thenReturn(token);

        LoginResult result = authService.login("sre@cloudpilot.dev", "correct-horse");

        assertThat(result.accessToken()).isEqualTo("jwt-value");
        assertThat(result.expiresAt()).isEqualTo(token.expiresAt());
        assertThat(result.user().email()).isEqualTo("sre@cloudpilot.dev");
        assertThat(result.user().role()).isEqualTo(Role.SRE);
    }

    @Test
    void emailIsTrimmedBeforeLookup() {
        when(users.findByEmailIgnoreCase("sre@cloudpilot.dev")).thenReturn(Optional.of(sre));
        when(tokens.issue(sre)).thenReturn(new IssuedToken("jwt", NOW));

        authService.login("  sre@cloudpilot.dev ", "correct-horse");

        verify(users).findByEmailIgnoreCase("sre@cloudpilot.dev");
    }

    @Test
    void wrongPasswordIsRejected() {
        when(users.findByEmailIgnoreCase("sre@cloudpilot.dev")).thenReturn(Optional.of(sre));

        assertThatThrownBy(() -> authService.login("sre@cloudpilot.dev", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokens, never()).issue(any());
    }

    @Test
    void unknownEmailGetsTheSameErrorAsWrongPassword() {
        when(users.findByEmailIgnoreCase("nobody@cloudpilot.dev")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@cloudpilot.dev", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void disabledUserCannotLogIn() {
        UserAccount disabled = new UserAccount("old@cloudpilot.dev", "Old", passwordEncoder.encode("pw"), Role.DEVELOPER, NOW);
        disabled.disable();
        when(users.findByEmailIgnoreCase("old@cloudpilot.dev")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> authService.login("old@cloudpilot.dev", "pw"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
