package dev.cloudpilot.support;

import java.time.Instant;

import dev.cloudpilot.auth.Role;
import dev.cloudpilot.auth.TokenService;
import dev.cloudpilot.auth.UserAccount;
import dev.cloudpilot.auth.UserAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for tests that need the full application and a real PostgreSQL.
 *
 * One container is started for the whole test run (static field, started once)
 * and Spring Boot wires the datasource to it through @ServiceConnection, so no
 * URLs or passwords live in test config. Same image as docker-compose, so
 * migrations are tested against exactly what runs in production.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // Tests create exactly the data they need; demo data would only get in the way.
                "cloudpilot.demo.seed-users=false",
                "cloudpilot.simulator.enabled=false"
        })
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserAccountRepository userAccounts;

    /**
     * Headers with a valid token for a user with the given role. The user is created
     * on first use, so tests don't have to go through the login endpoint.
     */
    protected HttpHeaders authHeaders(Role role) {
        String email = "it-" + role.name().toLowerCase() + "@test.dev";
        UserAccount user = userAccounts.findByEmailIgnoreCase(email)
                .orElseGet(() -> userAccounts.save(
                        new UserAccount(email, "IT " + role.name(), "not-used", role, Instant.now())));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.issue(user).value());
        return headers;
    }
}
