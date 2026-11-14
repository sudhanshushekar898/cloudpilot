package dev.cloudpilot.simulator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScenarioManagerTest {

    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    /** A clock the test can move forward. */
    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;
    private ScenarioManager manager;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(T0);
        manager = new ScenarioManager(clock);
    }

    @Test
    void nothingIsActiveAtStart() {
        assertThat(manager.active()).isEmpty();
    }

    @Test
    void startingAScenarioMakesItActiveForItsDuration() {
        ActiveScenario started = manager.start(ScenarioType.BAD_DEPLOYMENT, "admin@cloudpilot.dev");

        assertThat(started.startedAt()).isEqualTo(T0);
        assertThat(started.endsAt()).isEqualTo(T0.plus(ScenarioType.BAD_DEPLOYMENT.duration()));
        assertThat(started.startedBy()).isEqualTo("admin@cloudpilot.dev");
        assertThat(manager.active()).contains(started);
    }

    @Test
    void progressGrowsWithElapsedTime() {
        ActiveScenario started = manager.start(ScenarioType.BAD_DEPLOYMENT, "admin");   // 10 minutes

        clock.advance(Duration.ofMinutes(5));

        assertThat(started.progress(clock.instant())).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void scenarioExpiresOnItsOwn() {
        manager.start(ScenarioType.BAD_DEPLOYMENT, "admin");

        clock.advance(ScenarioType.BAD_DEPLOYMENT.duration());

        assertThat(manager.active()).isEmpty();
    }

    @Test
    void stopEndsTheScenarioImmediately() {
        manager.start(ScenarioType.QUEUE_BACKLOG, "admin");

        manager.stop();

        assertThat(manager.active()).isEmpty();
    }

    @Test
    void startingAnotherScenarioReplacesTheCurrentOne() {
        manager.start(ScenarioType.QUEUE_BACKLOG, "admin");

        manager.start(ScenarioType.POD_MEMORY_LEAK, "admin");

        assertThat(manager.active()).hasValueSatisfying(s -> assertThat(s.type()).isEqualTo(ScenarioType.POD_MEMORY_LEAK));
    }
}
