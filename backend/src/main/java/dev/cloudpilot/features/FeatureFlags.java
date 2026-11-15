package dev.cloudpilot.features;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dev.cloudpilot.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime kill switches stored in the database, so an admin can turn a feature off in seconds
 * without a redeploy (for example when the AI provider misbehaves or costs spike).
 */
@Service
public class FeatureFlags {

    public static final String CLAUDE_AI = "claude-ai";
    public static final String FAILURE_SCENARIOS = "failure-scenarios";

    private static final Logger log = LoggerFactory.getLogger(FeatureFlags.class);

    private final FeatureFlagRepository flags;
    private final Clock clock;

    public FeatureFlags(FeatureFlagRepository flags, Clock clock) {
        this.flags = flags;
        this.clock = clock;
    }

    /** Fail safe: an unknown flag name is off, so a typo can never switch a feature on. */
    @Transactional(readOnly = true)
    public boolean isEnabled(String name) {
        return flags.findById(name).map(FeatureFlag::isEnabled).orElse(false);
    }

    @Transactional(readOnly = true)
    public List<FlagView> list() {
        return flags.findAllByOrderByNameAsc().stream().map(FlagView::of).toList();
    }

    @Transactional
    public FlagView set(String name, boolean enabled, String actor) {
        FeatureFlag flag = flags.findById(name)
                .orElseThrow(() -> new NotFoundException("Unknown feature flag: " + name));
        flag.set(enabled, actor, Instant.now(clock));
        log.info("Feature flag {} switched {} by {}", name, enabled ? "on" : "off", actor);
        return FlagView.of(flag);
    }

    public record FlagView(String name, boolean enabled, String description, String updatedBy, Instant updatedAt) {

        static FlagView of(FeatureFlag flag) {
            return new FlagView(flag.getName(), flag.isEnabled(), flag.getDescription(), flag.getUpdatedBy(),
                    flag.getUpdatedAt());
        }
    }
}
