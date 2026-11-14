package dev.cloudpilot.auth;

import java.time.Instant;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserAccountRepositoryIntegrationTest extends IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-15T09:00:00Z");

    @Autowired
    private UserAccountRepository users;

    @AfterEach
    void cleanUp() {
        users.deleteAll();
    }

    @Test
    void findsUserByEmailIgnoringCase() {
        users.save(new UserAccount("Asha@Example.com", "Asha", "hash", Role.SRE, NOW));

        assertThat(users.findByEmailIgnoreCase("ASHA@example.COM"))
                .hasValueSatisfying(user -> {
                    assertThat(user.getEmail()).isEqualTo("asha@example.com");
                    assertThat(user.getRole()).isEqualTo(Role.SRE);
                });
    }

    @Test
    void rejectsDuplicateEmailsThatDifferOnlyInCase() {
        users.saveAndFlush(new UserAccount("dev@example.com", "Dev", "hash", Role.DEVELOPER, NOW));

        assertThatThrownBy(() -> users.saveAndFlush(
                new UserAccount("DEV@example.com", "Dev 2", "hash", Role.DEVELOPER, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
