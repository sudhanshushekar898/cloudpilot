package dev.cloudpilot.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates one account per role so the app can be tried right after `docker compose up`.
 * Idempotent: existing accounts are left alone, so restarts are safe.
 */
@Component
@EnableConfigurationProperties(DemoProperties.class)
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private record DemoUser(String email, String name, Role role) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("admin@cloudpilot.dev", "Ananya Admin", Role.ADMIN),
            new DemoUser("sre@cloudpilot.dev", "Rahul SRE", Role.SRE),
            new DemoUser("dev@cloudpilot.dev", "Meera Developer", Role.DEVELOPER));

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final DemoProperties properties;
    private final Clock clock;

    public DemoUserSeeder(UserAccountRepository users, PasswordEncoder passwordEncoder,
                          DemoProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.seedUsers()) {
            return;
        }
        for (DemoUser demo : DEMO_USERS) {
            if (users.existsByEmailIgnoreCase(demo.email())) {
                continue;
            }
            String hash = passwordEncoder.encode(properties.password());
            users.save(new UserAccount(demo.email(), demo.name(), hash, demo.role(), Instant.now(clock)));
            log.info("Created demo user {} with role {}", demo.email(), demo.role());
        }
    }
}
