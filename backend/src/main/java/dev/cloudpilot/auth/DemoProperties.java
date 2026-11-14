package dev.cloudpilot.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Demo data settings (cloudpilot.demo.*).
 *
 * @param seedUsers create one Admin, SRE and Developer account on startup if missing
 * @param password  password for the seeded accounts
 */
@ConfigurationProperties(prefix = "cloudpilot.demo")
public record DemoProperties(boolean seedUsers, String password) {

    public static final String DEFAULT_PASSWORD = "CloudPilot#2026";

    public DemoProperties {
        if (password == null || password.isBlank()) {
            password = DEFAULT_PASSWORD;
        }
    }
}
