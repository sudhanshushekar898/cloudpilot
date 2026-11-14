package dev.cloudpilot.system;

import java.util.Map;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class HealthIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void healthIsUpWhenDatabaseIsReachable() {
        ResponseEntity<Map> response = rest.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void flywayMigrationsEnablePgvector() {
        Integer count = jdbc.queryForObject(
                "select count(*) from pg_extension where extname = 'vector'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void pingWorksOverRealHttp() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/ping", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "ok");
    }
}
