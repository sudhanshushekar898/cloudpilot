# ADR-0005: Alert evaluation semantics (full-window firing, flap-safe resolve)

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

"API error rate > 5% for five minutes" can be implemented in several ways, and they behave
very differently during an incident:

| Approach | Problem |
|---|---|
| Fire on the latest sample | One noisy sample pages someone at 3 AM |
| Fire when the **average** over 5 min breaches | A 1-minute 40% spike averages above 5% and fires, though the condition never held for 5 minutes |
| Fire after N consecutive breaching runs (state machine in memory) | State is lost on restart; needs care with several instances |
| **Fire when every sample in the window breaches, and samples cover the window** | Chosen |

Resolving has the mirror problem: resolve on the first good sample and a struggling
service flaps between firing and resolved every few seconds.

## Decision

- The engine runs every 30 s. For each enabled rule it asks PostgreSQL for min, max, count and
  first sample time per resource in the rule's window (one query per rule).
- **Fire** when the worst-case value breaches (`min > t` for "above", `max < t` for "below")
  **and** the first sample is within 30 s of the window start. Without the coverage check, a
  resource with only 2 minutes of data could "hold for 5 minutes".
- **Resolve** only when every sample in the last 60 s is back within the threshold.
- **No data never resolves.** A resource that stops reporting hasn't recovered.
- **One firing alert per rule and resource**, checked by the engine and enforced by a partial
  unique index (`WHERE status = 'FIRING'`), so even overlapping runs can't create duplicates.
- Disabling a rule resolves its firing alerts.
- The engine is stateless between runs: everything it needs is in the database, so restarts
  are safe. The decision logic (`RuleEvaluator`) is a pure function with unit tests for every edge case.
- Firing and resolving publish `AlertFired` / `AlertResolved` events; the incidents module will
  listen without the alerts module knowing it exists.

## Consequences

- Alerts are trustworthy: if one fires, the condition really held for the stated duration.
- Detection delay is about the rule duration plus up to 30 s. That's intended; urgent conditions
  get short durations (availability: 2 min).
- With several backend instances, the scheduled job would need a lock (ShedLock) so only one
  instance evaluates at a time. The unique index already prevents duplicate alerts.
