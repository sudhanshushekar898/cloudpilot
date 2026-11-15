package dev.cloudpilot.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guardrails applied to every analysis, from any analyser (ADR-0002). The model is never
 * trusted to follow its instructions; this class checks the output against the evidence.
 *
 * <ul>
 *   <li>Citations must point at evidence that was actually provided; anything else is removed.</li>
 *   <li>Observations with no valid citation are dropped: "observed evidence" must be observed.</li>
 *   <li>A likely cause with no valid citation is kept, but its confidence is capped.</li>
 *   <li>Remediation steps that delete, terminate or purge are flagged for explicit human approval.
 *       CloudPilot never runs any step itself.</li>
 * </ul>
 */
public class AnalysisValidator {

    static final double UNSUPPORTED_CAUSE_MAX_CONFIDENCE = 0.3;

    /** Steps that destroy data, kill processes or drop work. Matched case-insensitively. */
    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "\\b(drop\\s+(table|database|index)|truncate|delete\\s+from|rm\\s+-rf|kubectl\\s+delete|purge|"
                    + "pg_terminate_backend|terminate|scale\\s+.*\\bto\\s+0\\b|force[- ]?push|failover)\\b",
            Pattern.CASE_INSENSITIVE);

    public record Validated(String summary, List<AnalysisDraft.Claim> observedEvidence, AnalysisDraft.Claim likelyCause,
                            double confidence, String confidenceReason, List<AnalysisDraft.Step> verification,
                            List<AnalysisResult.RemediationStep> remediation, List<String> warnings) {
    }

    public Validated validate(AnalysisDraft draft, EvidenceBundle bundle) {
        Set<String> known = bundle.ids();
        List<String> warnings = new ArrayList<>();

        List<AnalysisDraft.Claim> observed = new ArrayList<>();
        for (AnalysisDraft.Claim claim : orEmpty(draft.observedEvidence())) {
            AnalysisDraft.Claim cleaned = clean(claim, known, warnings);
            if (cleaned.citations().isEmpty()) {
                warnings.add("Dropped an observation with no supporting evidence: \"" + cleaned.text() + "\"");
            } else {
                observed.add(cleaned);
            }
        }

        AnalysisDraft.Claim cause = clean(draft.likelyCause() == null
                ? new AnalysisDraft.Claim("No likely cause was identified.", List.of())
                : draft.likelyCause(), known, warnings);

        double confidence = Double.isNaN(draft.confidence()) ? 0.0 : Math.max(0.0, Math.min(1.0, draft.confidence()));
        if (cause.citations().isEmpty() && confidence > UNSUPPORTED_CAUSE_MAX_CONFIDENCE) {
            confidence = UNSUPPORTED_CAUSE_MAX_CONFIDENCE;
            warnings.add("The likely cause is not backed by cited evidence, so confidence was lowered to "
                    + UNSUPPORTED_CAUSE_MAX_CONFIDENCE + ".");
        }

        List<AnalysisDraft.Step> verification = orEmpty(draft.verification()).stream()
                .map(step -> cleanStep(step, known, warnings)).toList();

        List<AnalysisResult.RemediationStep> remediation = new ArrayList<>();
        int risky = 0;
        for (AnalysisDraft.Step step : orEmpty(draft.remediation())) {
            AnalysisDraft.Step cleaned = cleanStep(step, known, warnings);
            boolean destructive = DESTRUCTIVE.matcher(cleaned.text()).find();
            risky += destructive ? 1 : 0;
            remediation.add(new AnalysisResult.RemediationStep(cleaned.text(), cleaned.citations(), destructive));
        }
        if (risky > 0) {
            warnings.add(risky + " remediation step" + (risky == 1 ? " changes or deletes" : "s change or delete")
                    + " something and must be approved by a person before anyone runs it.");
        }

        return new Validated(draft.summary() == null ? "" : draft.summary().trim(), observed, cause, confidence,
                draft.confidenceReason() == null ? "" : draft.confidenceReason().trim(), verification, remediation,
                warnings);
    }

    private static AnalysisDraft.Claim clean(AnalysisDraft.Claim claim, Set<String> known, List<String> warnings) {
        return new AnalysisDraft.Claim(text(claim.text()), validCitations(claim.citations(), known, warnings));
    }

    private static AnalysisDraft.Step cleanStep(AnalysisDraft.Step step, Set<String> known, List<String> warnings) {
        return new AnalysisDraft.Step(text(step.text()), validCitations(step.citations(), known, warnings));
    }

    private static List<String> validCitations(List<String> citations, Set<String> known, List<String> warnings) {
        List<String> valid = new ArrayList<>();
        for (String citation : orEmpty(citations)) {
            String id = citation == null ? "" : citation.trim();
            if (known.contains(id)) {
                if (!valid.contains(id)) {
                    valid.add(id);
                }
            } else {
                warnings.add("Removed citation " + id + ": it does not refer to any evidence.");
            }
        }
        return valid;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
