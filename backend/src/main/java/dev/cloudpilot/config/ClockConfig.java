package dev.cloudpilot.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable clock. Code asks for the time through this bean instead of
 * calling Instant.now() directly, so tests can freeze time (important later for
 * alert windows like "error rate > 5% for 5 minutes").
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
