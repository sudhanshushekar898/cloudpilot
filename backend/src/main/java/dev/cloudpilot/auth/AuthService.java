package dev.cloudpilot.auth;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    /**
     * Hash of a random password, checked when the email doesn't exist. Without it an
     * unknown email returns faster than a wrong password (no BCrypt work), and an
     * attacker could time responses to find out which emails are registered.
     */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-safety");
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password) {
        Optional<UserAccount> found = users.findByEmailIgnoreCase(email.trim());

        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw new InvalidCredentialsException();
        }

        UserAccount user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash()) || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        IssuedToken token = tokens.issue(user);
        return new LoginResult(token.value(), token.expiresAt(), LoginResult.UserSummary.of(user));
    }
}
