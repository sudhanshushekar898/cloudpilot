# ADR-0009: AI-drafted, human-approved postmortems; defined reliability metrics

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

After an incident the team needs a postmortem (what happened, why, what we'll change) and, over
time, numbers that show whether reliability is improving. Writing postmortems by hand takes hours
and often doesn't happen. Letting an AI write them alone is risky: it can invent impact numbers,
blame the wrong cause, or produce a document nobody actually reviewed.

## Decision

**Postmortems**
- Only a **resolved** incident can get a postmortem (409 otherwise): the resolution note is a key fact.
- The **timeline is built from recorded events** (alerts, status changes, assignments, comments,
  deployments) and is never written or edited by the model or by people. Facts stay facts.
- The narrative fields (summary, impact, root cause, resolution, prevention actions) are drafted by
  Claude when configured, otherwise by a deterministic template. Claude gets the facts only, with
  instructions to be blameless and use only numbers present in the facts; blank fields are filled
  from the template. Any Claude error falls back to the template.
- The root-cause draft quotes the latest incident analysis (analyser and confidence) and asks a
  person to confirm or correct it. Without an analysis it says "To be written".
- SREs and admins edit the draft and **approve** it. Approval needs a root cause and at least one
  prevention action, records who approved it, and **locks** the document (edits and redrafts → 409).
  Developers can read it.
- The postmortem records whether a model or the template drafted it.

**Reliability metrics** (pure `ReportCalculator`, each definition unit-tested)
- **Window:** incidents *opened* in the last 1–365 days.
- **MTTA:** mean of acknowledged − opened, over acknowledged incidents.
- **MTTR:** mean of resolved − opened, over resolved incidents. It starts at the first alert, so time
  before detection is not included; that's stated in the UI.
- **SLA:** resolution targets per severity (critical 60 min, warning 4 h, info 24 h); compliance is
  the share of resolved incidents within target.
- **Recurring failure:** the same first-affected resource and the same first-alert text once numbers
  are removed (`orders-db cpu #% > #% for # min`), at least twice in the window.
- **Responders:** resolved incidents and mean time to resolve per assignee.
- Averages with nothing to average are `null` and shown as "No data", never as 0.

## Consequences

- Writing a postmortem becomes editing a draft, which makes it far more likely to happen.
- Approved postmortems are trustworthy records: who approved what, and when, cannot change.
- Responder stats can be misused as performance scores. They are shown to help balance on-call
  load, and are intentionally simple (no ranking by speed alone).
- Computing reports in Java is fine for hundreds of incidents; at larger scale the aggregates
  move into SQL.
