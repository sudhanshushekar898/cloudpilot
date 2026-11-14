package dev.cloudpilot.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-02 (login) and US-03 (role-based access) through real HTTP, the real security
 * filter chain and a real PostgreSQL database.
 */
class AuthFlowIntegrationTest extends IntegrationTest {

    private static final String PASSWORD = "Str0ng-pass!";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void createUsers() {
        Instant now = Instant.now();
        String hash = passwordEncoder.encode(PASSWORD);
        users.saveAll(List.of(
                new UserAccount("admin@test.dev", "Admin", hash, Role.ADMIN, now),
                new UserAccount("sre@test.dev", "SRE", hash, Role.SRE, now),
                new UserAccount("dev@test.dev", "Developer", hash, Role.DEVELOPER, now)));
    }

    @AfterEach
    void cleanUp() {
        users.deleteAll();
    }

    private ResponseEntity<Map> login(String email, String password) {
        return rest.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", password), Map.class);
    }

    private String tokenFor(String email) {
        return (String) login(email, PASSWORD).getBody().get("accessToken");
    }

    private ResponseEntity<Map> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private HttpStatus statusOf(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return (HttpStatus) rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode();
    }

    @Test
    void validLoginReturnsBearerTokenAndUser() {
        ResponseEntity<Map> response = login("sre@test.dev", PASSWORD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("tokenType", "Bearer");
        assertThat((String) response.getBody().get("accessToken")).isNotBlank();
        assertThat((Map<String, Object>) response.getBody().get("user"))
                .containsEntry("email", "sre@test.dev")
                .containsEntry("role", "SRE");
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveIdenticalGeneric401() {
        ResponseEntity<Map> wrongPassword = login("sre@test.dev", "nope");
        ResponseEntity<Map> unknownEmail = login("ghost@test.dev", "nope");

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody()).containsEntry("detail", "Invalid email or password");
        assertThat(unknownEmail.getBody()).containsEntry("detail", "Invalid email or password");
    }

    @Test
    void malformedLoginRequestIsRejectedWith400() {
        ResponseEntity<Map> response = login("not-an-email", "");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void meReturnsTheLoggedInUser() {
        ResponseEntity<Map> response = getWithToken("/api/v1/auth/me", tokenFor("dev@test.dev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .containsEntry("email", "dev@test.dev")
                .containsEntry("role", "DEVELOPER");
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/auth/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tamperedTokenReturns401() {
        String token = tokenFor("dev@test.dev");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThat(getWithToken("/api/v1/auth/me", tampered).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyAdminsCanListUsers() {
        assertThat(statusOf("/api/v1/users", tokenFor("admin@test.dev"))).isEqualTo(HttpStatus.OK);
        assertThat(statusOf("/api/v1/users", tokenFor("sre@test.dev"))).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf("/api/v1/users", tokenFor("dev@test.dev"))).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
