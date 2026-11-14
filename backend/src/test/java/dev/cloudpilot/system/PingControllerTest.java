package dev.cloudpilot.system;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import dev.cloudpilot.auth.JwtConfig;
import dev.cloudpilot.auth.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PingController.class)
@Import({PingControllerTest.FixedClockConfig.class, SecurityConfig.class, JwtConfig.class})
class PingControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void pingReturnsOkStatus() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void pingIncludesServiceNameAndTimestamp() throws Exception {
        mockMvc.perform(get("/api/v1/ping"))
                .andExpect(jsonPath("$.service").value("cloudpilot"))
                .andExpect(jsonPath("$.timestamp").value("2026-11-14T10:00:00Z"));
    }
}
