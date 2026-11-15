package dev.cloudpilot.auth;

import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final MeterRegistry registry;

    /**
     * Hash of a random password, checked when the email doesn't exist. Without it an
     * unknown email returns faster than a wrong password (no BCrypt work), and an
     * attacker could time responses to find out which emails are registered.
     */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       MeterRegistry registry) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.registry = registry;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-safety");
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        Optional<UserAccount> found = users.findByEmailIgnoreCase(email.trim());

        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            failed();
        }

        UserAccount user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isEnabled()) {
            failed();
        }

        IssuedToken token = tokens.issue(user);
        return new LoginResult(token.value(), token.expiresAt(), LoginResult.UserSummary.of(user));
    }

    /**
     * Failed logins are a security signal (brute force, credential stuffing), so they are logged
     * and counted. The email is left out on purpose: logs shouldn't collect personal data, and
     * the request id already ties the line to the request.
     */
    private void failed() {
        log.warn("Failed login attempt");
        registry.counter("cloudpilot.auth.login.failed").increment();
        throw new InvalidCredentialsException();
    }
}
