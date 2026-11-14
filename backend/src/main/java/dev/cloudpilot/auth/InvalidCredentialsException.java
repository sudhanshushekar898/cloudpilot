package dev.cloudpilot.auth;

/**
 * Thrown for any failed login. The message is deliberately the same for an unknown
 * email, a wrong password and a disabled account, so the response never reveals
 * which emails are registered (user enumeration).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
