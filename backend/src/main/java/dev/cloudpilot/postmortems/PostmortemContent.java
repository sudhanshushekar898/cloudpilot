package dev.cloudpilot.postmortems;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The parts of a postmortem a person writes or edits. Also the JSON schema an AI draft must
 * follow, so the descriptions double as instructions. The timeline is NOT here: it is built
 * from recorded events, never written by the model.
 */
public record PostmortemContent(
        @JsonPropertyDescription("Two or three sentences: what happened, for how long, and how it was fixed.")
        String summary,
        @JsonPropertyDescription("Who and what was affected, for how long, and how badly. Use only numbers from the evidence.")
        String impact,
        @JsonPropertyDescription("The technical root cause, as supported by the evidence and the recorded resolution.")
        String rootCause,
        @JsonPropertyDescription("What was done to restore service.")
        String resolution,
        @JsonPropertyDescription("Concrete follow-up actions that would prevent a repeat or shorten detection, one per item.")
        List<String> prevention) {
}
