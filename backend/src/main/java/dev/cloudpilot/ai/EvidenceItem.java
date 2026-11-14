package dev.cloudpilot.ai;

import java.time.Instant;

/**
 * One piece of evidence the analysis may cite, with a short stable id:
 * A1 (alert), L1 (log line), D1 (deployment), R1 (runbook section).
 *
 * The model only ever sees these items, and every claim it makes must cite their ids.
 */
public record EvidenceItem(String id, Kind kind, String resourceName, Instant occurredAt, String text, String source) {

    public enum Kind {
        ALERT("A"), LOG("L"), DEPLOYMENT("D"), RUNBOOK("R");

        private final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }
}
