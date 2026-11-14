package dev.cloudpilot.simulator;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RampTest {

    @ParameterizedTest(name = "{0} at {1} -> {2}")
    @CsvSource({
            "FAST,   0.0,  0.0",
            "FAST,   0.1,  0.5",
            "FAST,   0.2,  1.0",
            "FAST,   0.9,  1.0",
            "LINEAR, 0.0,  0.0",
            "LINEAR, 0.5,  0.5",
            "LINEAR, 1.0,  1.0",
            "LINEAR, 1.5,  1.0",
            "LINEAR, -1,   0.0",
    })
    void factorFollowsTheRampShape(Ramp ramp, double progress, double expected) {
        assertThat(ramp.factor(progress)).isCloseTo(expected, within(1e-9));
    }
}
