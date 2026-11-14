package dev.cloudpilot.ai;

/** Produces a draft analysis from evidence. Drafts are always checked by {@link AnalysisValidator}. */
public interface Analyzer {

    /** Short name stored with the analysis, e.g. "claude" or "rule-based". */
    String name();

    /** Model id when a language model is used, otherwise null. */
    String model();

    AnalysisDraft analyze(EvidenceBundle bundle);
}
