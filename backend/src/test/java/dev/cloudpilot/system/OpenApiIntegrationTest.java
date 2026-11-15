package dev.cloudpilot.system;

import java.util.Map;

import dev.cloudpilot.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** The API is documented from the code itself, so the docs can't drift from what's deployed. */
class OpenApiIntegrationTest extends IntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void publishesAnOpenApiSpecWithEveryModulesEndpoints() {
        ResponseEntity<Map> response = rest.getForEntity("/v3/api-docs", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> paths = (Map<String, Object>) response.getBody().get("paths");
        assertThat(paths).containsKeys(
                "/api/v1/auth/login",
                "/api/v1/resources",
                "/api/v1/alerts",
                "/api/v1/incidents/{id}",
                "/api/v1/incidents/{incidentId}/analysis",
                "/api/v1/incidents/{incidentId}/postmortem",
                "/api/v1/runbooks/search",
                "/api/v1/reports/summary");
        Map<String, Object> info = (Map<String, Object>) response.getBody().get("info");
        assertThat(info).containsEntry("title", "CloudPilot API");
    }

    @Test
    void declaresJwtBearerAuthentication() {
        Map<String, Object> spec = rest.getForEntity("/v3/api-docs", Map.class).getBody();

        Map<String, Object> schemes = (Map<String, Object>) ((Map<String, Object>) spec.get("components")).get("securitySchemes");
        assertThat((Map<String, Object>) schemes.get("bearer-jwt"))
                .containsEntry("type", "http").containsEntry("scheme", "bearer").containsEntry("bearerFormat", "JWT");
    }

    @Test
    void swaggerUiIsReachableWithoutLoggingIn() {
        ResponseEntity<String> ui = rest.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(ui.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ui.getBody()).contains("Swagger UI");
    }
}
