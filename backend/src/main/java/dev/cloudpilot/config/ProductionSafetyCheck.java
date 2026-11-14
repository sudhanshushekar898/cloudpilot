package dev.cloudpilot.config;

import dev.cloudpilot.auth.DemoProperties;
import dev.cloudpilot.auth.JwtProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails fast on startup in the prod profile if insecure development defaults are still
 * in place. A crashed deploy is better than a running one that anyone can forge tokens for.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    private final JwtProperties jwt;
    private final DemoProperties demo;

    public ProductionSafetyCheck(JwtProperties jwt, DemoProperties demo) {
        this.jwt = jwt;
        this.demo = demo;
    }

    @PostConstruct
    void verify() {
        if (JwtProperties.DEV_SECRET.equals(jwt.secret())) {
            throw new IllegalStateException(
                    "Refusing to start: set the JWT_SECRET environment variable for the prod profile");
        }
        if (demo.seedUsers() && DemoProperties.DEFAULT_PASSWORD.equals(demo.password())) {
            throw new IllegalStateException(
                    "Refusing to start: set DEMO_PASSWORD or disable DEMO_SEED_USERS for the prod profile");
        }
    }
}
