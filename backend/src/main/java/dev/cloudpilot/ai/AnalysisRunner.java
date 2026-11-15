package dev.cloudpilot.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the preferred analyser (Claude, if configured), falls back to the rule-based one on any
 * error or refusal, and always passes the result through the guardrails. An incident page must
 * get an answer even when the AI provider is down. An admin kill switch can skip the LLM at runtime.
 */
public class AnalysisRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalysisRunner.class);

    private final Optional<Analyzer> preferred;
    private final Analyzer fallback;
    private final AnalysisValidator validator;
    private final BooleanSupplier llmEnabled;

    public AnalysisRunner(Optional<Analyzer> preferred, Analyzer fallback, AnalysisValidator validator) {
        this(preferred, fallback, validator, () -> true);
    }

    public AnalysisRunner(Optional<Analyzer> preferred, Analyzer fallback, AnalysisValidator validator,
                          BooleanSupplier llmEnabled) {
        this.preferred = preferred;
        this.fallback = fallback;
        this.validator = validator;
        this.llmEnabled = llmEnabled;
    }

    /** @param fellBack true when the preferred (LLM) analyser failed or refused and the fallback answered */
    public record Outcome(AnalysisValidator.Validated validated, String analyzer, String model, boolean fellBack) {
    }

    public Outcome run(EvidenceBundle bundle) {
        List<String> notes = new ArrayList<>();
        if (preferred.isPresent() && !llmEnabled.getAsBoolean()) {
            notes.add("AI analysis is switched off by an admin, so this is the rule-based analysis.");
            return outcome(fallback, fallback.analyze(bundle), bundle, notes, false);
        }
        if (preferred.isPresent()) {
            Analyzer llm = preferred.get();
            try {
                return outcome(llm, llm.analyze(bundle), bundle, notes, false);
            } catch (ClaudeRefusalException e) {
                log.warn("LLM analysis refused for {}: {}", bundle.incidentKey(), e.getMessage());
                notes.add("The AI model declined to answer, so this is the rule-based analysis instead.");
            } catch (RuntimeException e) {
                log.warn("LLM analysis failed for {}, using rule-based fallback", bundle.incidentKey(), e);
                notes.add("The AI model was unavailable, so this is the rule-based analysis instead.");
            }
        }
        return outcome(fallback, fallback.analyze(bundle), bundle, notes, !notes.isEmpty());
    }

    private Outcome outcome(Analyzer analyzer, AnalysisDraft draft, EvidenceBundle bundle, List<String> notes,
                            boolean fellBack) {
        AnalysisValidator.Validated validated = validator.validate(draft, bundle);
        if (!notes.isEmpty()) {
            List<String> warnings = new ArrayList<>(notes);
            warnings.addAll(validated.warnings());
            validated = new AnalysisValidator.Validated(validated.summary(), validated.observedEvidence(),
                    validated.likelyCause(), validated.confidence(), validated.confidenceReason(),
                    validated.verification(), validated.remediation(), warnings);
        }
        return new Outcome(validated, analyzer.name(), analyzer.model(), fellBack);
    }
}
