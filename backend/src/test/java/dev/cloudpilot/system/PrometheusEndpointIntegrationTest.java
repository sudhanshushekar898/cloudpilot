package dev.cloudpilot.system;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusEndpointIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void exposesMetricsInPrometheusFormat() {
        rest.getForEntity("/api/v1/ping", String.class);   // make sure at least one HTTP request was recorded

        ResponseEntity<String> response = rest.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("jvm_memory_used_bytes")
                .contains("http_server_requests_seconds_bucket")
                .contains("application=\"cloudpilot\"");
    }
}
