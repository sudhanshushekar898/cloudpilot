package dev.cloudpilot.config;

import java.time.Duration;

import dev.cloudpilot.auth.DemoProperties;
import dev.cloudpilot.auth.JwtProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyCheckTest {

    private static final String REAL_SECRET = "a-real-production-secret-from-env-0123456789";

    private static JwtProperties jwt(String secret) {
        return new JwtProperties(secret, Duration.ofHours(1), "cloudpilot");
    }

    @Test
    void refusesTheDevelopmentJwtSecret() {
        ProductionSafetyCheck check = new ProductionSafetyCheck(
                jwt(JwtProperties.DEV_SECRET), new DemoProperties(false, null));

        assertThatThrownBy(check::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesDemoUsersWithTheDefaultPassword() {
        ProductionSafetyCheck check = new ProductionSafetyCheck(
                jwt(REAL_SECRET), new DemoProperties(true, DemoProperties.DEFAULT_PASSWORD));

        assertThatThrownBy(check::verify)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_PASSWORD");
    }

    @Test
    void acceptsARealSecretAndCustomDemoPassword() {
        ProductionSafetyCheck check = new ProductionSafetyCheck(
                jwt(REAL_SECRET), new DemoProperties(true, "a-custom-demo-password"));

        assertThatCode(check::verify).doesNotThrowAnyException();
    }
}
