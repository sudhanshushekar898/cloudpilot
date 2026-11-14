package dev.cloudpilot.alerts;

/** Comparison a rule applies: "value is above / below the threshold". */
public enum Operator {
    GT(">") {
        @Override
        public boolean breaches(double value, double threshold) {
            return value > threshold;
        }
    },
    LT("<") {
        @Override
        public boolean breaches(double value, double threshold) {
            return value < threshold;
        }
    };

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public abstract boolean breaches(double value, double threshold);

    public String symbol() {
        return symbol;
    }
}
