package dev.cloudpilot.system;

import dev.cloudpilot.config.RequestIdFilter;
import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void everyResponseCarriesARequestIdEvenWhenSecurityRejectsIt() {
        ResponseEntity<String> rejected = rest.getForEntity("/api/v1/auth/me", String.class);

        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rejected.getHeaders().getFirst(RequestIdFilter.HEADER)).isNotBlank();
    }

    @Test
    void eachRequestGetsItsOwnId() {
        String first = rest.getForEntity("/api/v1/ping", String.class).getHeaders().getFirst(RequestIdFilter.HEADER);
        String second = rest.getForEntity("/api/v1/ping", String.class).getHeaders().getFirst(RequestIdFilter.HEADER);

        assertThat(first).isNotEqualTo(second);
    }
}
