package dev.cloudpilot.system;

import java.time.Clock;
import java.time.Instant;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight liveness check used by the web app and smoke tests.
 * Unlike /actuator/health it never touches the database, so it answers
 * "is the API process up?" and nothing more.
 */
@RestController
@RequestMapping("/api/v1/ping")
public class PingController {

    private final Clock clock;

    public PingController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    @SecurityRequirements   // public liveness check
    public PingResponse ping() {
        return new PingResponse("ok", "cloudpilot", Instant.now(clock));
    }

    public record PingResponse(String status, String service, Instant timestamp) {
    }
}
