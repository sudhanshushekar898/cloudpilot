package dev.cloudpilot.simulator;

/** How fast a scenario's effect reaches full strength, as a function of progress 0..1. */
public enum Ramp {
    /** Sudden failure (bad deploy, pool exhausted): full effect after 20% of the scenario. */
    FAST {
        @Override
        public double factor(double progress) {
            return clamp(progress / 0.2);
        }
    },
    /** Slow degradation (memory leak): grows steadily until the end. */
    LINEAR {
        @Override
        public double factor(double progress) {
            return clamp(progress);
        }
    };

    public abstract double factor(double progress);

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
