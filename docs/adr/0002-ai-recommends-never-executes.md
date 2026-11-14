# ADR-0002: The AI recommends, it never executes

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

CloudPilot uses an LLM to suggest root causes and remediation steps during incidents.
LLMs can be wrong with confidence, and their output can be steered by text inside logs or
documents (prompt injection). An incident is the worst time to let an unverified action run
against production.

## Decision

1. **No execution path.** The `ai` module has no access to shells, cloud SDKs or any API that
   changes infrastructure. Remediation steps are returned as plain text for a human to run.
2. **Evidence-only input.** The prompt contains only the incident's metrics, logs, deployment
   events and retrieved runbook sections. Nothing else from the system is sent.
3. **Structured output.** The model must answer in a fixed JSON shape:
   observed evidence, likely cause, confidence (0–1), recommended verification, safe remediation
   steps, and citations. Output that doesn't parse is rejected, not shown.
4. **Citations are checked.** Every citation must point to a runbook chunk or event that was
   actually in the prompt. An answer with no valid citation is downgraded to low confidence.
5. **Logs are data, not instructions.** Log text is wrapped and labelled as untrusted in the
   prompt, so instructions hidden in logs are not followed.
6. **Human approval.** Postmortems drafted by the AI stay in `DRAFT` until an SRE edits and
   approves them.

## Consequences

- Engineers stay in control and accountable; the AI shortens investigation, not judgment.
- Some answers will be shown as "low confidence", which is honest but less impressive in a demo.
- A rule-based analyser implements the same interface, so the app works without an API key
  and tests don't depend on a paid model.
