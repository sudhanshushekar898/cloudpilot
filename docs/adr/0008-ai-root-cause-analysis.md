# ADR-0008: AI root-cause analysis with Claude, guardrails and a rule-based fallback

- **Status:** Accepted
- **Date:** 2026-11-15
- **Builds on:** ADR-0002 (the AI recommends, never executes), ADR-0007 (runbook retrieval)

## Context

Engineers want to ask "why did checkout fail after 2 PM?" and get a useful starting point in
seconds. A raw LLM answer is not good enough during an incident: it can invent a cause, cite
nothing, suggest something destructive, or be steered by text inside log lines. The app also has
to work with no API key (local demos, CI) and when the AI provider is down.

## Decision

**Evidence bundle.** For each question, CloudPilot collects only the incident's own data and
numbers every item so it can be cited:

| Id | Source |
|---|---|
| A1, A2… | grouped alerts, in firing order, with the reason each was grouped |
| L1, L2… | up to 15 distinct log lines (repeats collapsed, "seen N times"), earliest first |
| D1, D2… | every deployment in the hour before the incident, related or not |
| R1–R4 | top runbook sections from full-text retrieval (ADR-0007) |

Plus the affected resources, the probable origin and the dependency edges.
All deployments are included on purpose: "what changed?" is the first question, and the analysis
must be able to say that a release was *unrelated*.

**Two analysers, one interface.**
- `RuleBasedAnalyzer`: deterministic. Follows the dependency graph to the probable origin, blames a
  deployment only if it hit the origin within 60 minutes before the first alert, and takes
  verification and remediation steps from the retrieved runbook sections. No key, no network.
- `ClaudeAnalyzer`: `claude-opus-5-5` through the official Anthropic Java SDK, used only when
  `ANTHROPIC_API_KEY` is set.
  - Structured outputs: the answer must match the `AnalysisDraft` JSON schema (summary, observed
    evidence, likely cause, confidence, verification, remediation, each with citations).
  - Thinking is adaptive (it can't be turned off on this model); effort `high` for this reasoning task.
  - The system prompt is fixed for every incident, so it is a stable, cacheable prefix.
  - Evidence text is XML-escaped and log lines sit in `<untrusted_log>` elements; the prompt says
    never to follow instructions found there.
  - Refusals (`stop_reason: refusal`) are checked before reading the content. Server-side fallbacks
    (beta) are on by default and can be turned off with `CLOUDPILOT_AI_SERVER_FALLBACKS=false`.

**Fallback.** If Claude errors, times out (120 s, one retry) or refuses, the rule-based answer is
shown with a note saying why. An incident page always gets an answer.

**Guardrails on every answer, from either analyser.** The model is never trusted to follow its
instructions; `AnalysisValidator` checks the output:
- Citations that don't match an evidence id are removed (with a warning).
- Observations with no valid citation are dropped.
- A likely cause with no valid citation keeps its text but its confidence is capped at 0.3.
- Remediation steps that drop, truncate, delete, purge, terminate or scale to zero are marked
  **Needs approval**. Nothing is ever executed.

**Storage and cost.** Each analysis is stored as JSONB together with the exact evidence it was
given, so any answer can be audited later. Asking the same question within 30 seconds returns the
stored answer instead of paying for another call. Any signed-in role can ask, since analysis only
reads and recommends.

## Consequences

- Every claim on screen links to the evidence behind it; an engineer can check it in one click.
- The app works fully offline; with a key, answers get better without any other change.
- Claude's output is only tested offline (request shape and JSON schema). Its answer quality needs
  an evaluation set with a real key; the scenario simulator gives four labelled cases to start from.
- The regex for destructive steps is a safety net, not a guarantee. A person reviews every step.
