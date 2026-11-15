package dev.cloudpilot.features;

import java.util.List;
import java.util.Map;

import dev.cloudpilot.auth.Role;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Kill switches: admins flip them at runtime, everyone can see their state. */
class FeatureFlagApiIntegrationTest extends IntegrationTest {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {};

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private FeatureFlags flags;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void resetFlags() {
        jdbc.update("update feature_flags set enabled = true");
    }

    private <T> ResponseEntity<T> call(HttpMethod method, String path, Object body, Role role,
                                       ParameterizedTypeReference<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, authHeaders(role)), type);
    }

    @Test
    void anyoneCanSeeTheFlags() {
        List<Map<String, Object>> list = call(HttpMethod.GET, "/api/v1/features", null, Role.DEVELOPER, LIST).getBody();

        assertThat(list).extracting(f -> f.get("name")).containsExactly("claude-ai", "failure-scenarios");
        assertThat(list).allSatisfy(f -> assertThat(f).containsEntry("enabled", true).containsKey("description"));
    }

    @Test
    void anAdminCanSwitchAFlagOffAndItTakesEffectImmediately() {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.PUT, "/api/v1/features/claude-ai",
                Map.of("enabled", false), Role.ADMIN, MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("enabled", false).containsEntry("updatedBy", "it-admin@test.dev");
        assertThat(flags.isEnabled(FeatureFlags.CLAUDE_AI)).isFalse();
    }

    @Test
    void onlyAdminsCanChangeFlags() {
        assertThat(call(HttpMethod.PUT, "/api/v1/features/claude-ai", Map.of("enabled", false), Role.SRE, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(flags.isEnabled(FeatureFlags.CLAUDE_AI)).isTrue();
    }

    @Test
    void unknownFlagIs404() {
        assertThat(call(HttpMethod.PUT, "/api/v1/features/teleportation", Map.of("enabled", true), Role.ADMIN, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void switchingScenariosOffBlocksStartingOne() {
        call(HttpMethod.PUT, "/api/v1/features/failure-scenarios", Map.of("enabled", false), Role.ADMIN, MAP);

        ResponseEntity<Map<String, Object>> start = call(HttpMethod.POST,
                "/api/v1/simulator/scenarios/BAD_DEPLOYMENT/start", null, Role.ADMIN, MAP);

        assertThat(start.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(start.getBody()).containsEntry("detail", "Failure scenarios are switched off by an admin");
    }

    @Test
    void anUnknownFlagNameIsTreatedAsOffInCode() {
        // Fail safe: a typo in a flag name must never accidentally turn a feature on.
        assertThat(flags.isEnabled("no-such-flag")).isFalse();
    }
}
