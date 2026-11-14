package dev.cloudpilot.resources;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.cloudpilot.auth.Role;
import dev.cloudpilot.metrics.MetricSample;
import dev.cloudpilot.metrics.MetricStore;
import dev.cloudpilot.metrics.MetricType;
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

/** US-04: resource dashboard data, through real HTTP and real PostgreSQL. */
class ResourceApiIntegrationTest extends IntegrationTest {

    private static final UUID ORDERS_DB = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID CHECKOUT_API = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MetricStore metrics;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from metric_samples");
    }

    private <T> ResponseEntity<T> get(String path, ParameterizedTypeReference<T> type) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(Role.DEVELOPER)), type);
    }

    private ResponseEntity<List<Map<String, Object>>> listResources() {
        return get("/api/v1/resources", new ParameterizedTypeReference<>() {});
    }

    private static Map<String, Object> byName(List<Map<String, Object>> resources, String name) {
        return resources.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElseThrow();
    }

    @Test
    void listsSeededResourcesSortedByNameWithDependencies() {
        ResponseEntity<List<Map<String, Object>>> response = listResources();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(r -> r.get("name")).containsExactly(
                "catalog-api", "checkout-api", "inventory-pod", "orders-db",
                "payments-queue", "payments-worker", "web-frontend");

        Map<String, Object> checkout = byName(response.getBody(), "checkout-api");
        assertThat(checkout).containsEntry("type", "API");
        assertThat((List<String>) checkout.get("dependsOn"))
                .containsExactlyInAnyOrder(ORDERS_DB.toString(), "00000000-0000-0000-0000-000000000005");
    }

    @Test
    void statusReflectsLatestMetrics() {
        Instant now = Instant.now();
        metrics.saveAll(List.of(
                new MetricSample(ORDERS_DB, MetricType.ERROR_RATE, 12.0, now),
                new MetricSample(CHECKOUT_API, MetricType.ERROR_RATE, 0.2, now),
                new MetricSample(CHECKOUT_API, MetricType.AVAILABILITY, 100.0, now)));

        List<Map<String, Object>> resources = listResources().getBody();

        assertThat(byName(resources, "orders-db")).containsEntry("status", "DEGRADED");
        assertThat(byName(resources, "checkout-api")).containsEntry("status", "HEALTHY");
        assertThat(byName(resources, "web-frontend")).containsEntry("status", "UNKNOWN");
        assertThat((Map<String, Object>) byName(resources, "orders-db").get("latest"))
                .containsEntry("ERROR_RATE", 12.0);
    }

    @Test
    void returnsMetricHistoryForOneResource() {
        Instant now = Instant.now();
        metrics.saveAll(List.of(
                new MetricSample(ORDERS_DB, MetricType.CPU, 40, now.minusSeconds(120)),
                new MetricSample(ORDERS_DB, MetricType.CPU, 60, now.minusSeconds(30))));

        ResponseEntity<Map<String, Object>> response =
                get("/api/v1/resources/" + ORDERS_DB + "/metrics?minutes=15", new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("minutes", 15);
        Map<String, List<?>> series = (Map<String, List<?>>) response.getBody().get("series");
        assertThat(series.get("CPU")).hasSize(2);
    }

    @Test
    void unknownResourceIs404() {
        ResponseEntity<Map<String, Object>> response =
                get("/api/v1/resources/" + UUID.randomUUID(), new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historyRangeIsValidated() {
        ResponseEntity<Map<String, Object>> response =
                get("/api/v1/resources/" + ORDERS_DB + "/metrics?minutes=0", new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void requiresAuthentication() {
        assertThat(rest.getForEntity("/api/v1/resources", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
