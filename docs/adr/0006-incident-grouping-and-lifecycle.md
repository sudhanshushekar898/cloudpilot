# ADR-0006: Group alerts by dependency graph; incidents follow a strict lifecycle

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

One failing database fires alerts on itself and on every service that calls it. Paging an
engineer five times for one problem is the alert fatigue CloudPilot exists to fix. We also need
a clear record of who owned an incident and what was done.

Grouping options considered:

| Option | Problem |
|---|---|
| Group by time only ("everything within 10 min") | Unrelated failures merge into one incident |
| Group by resource only | One root cause still becomes N incidents |
| ML clustering | Unexplainable and needs training data we don't have |
| **Dependency graph + time window** | Chosen: explainable and uses data we already have |

## Decision

**Grouping**
- An alert joins an active incident if, within 10 minutes of the incident's last alert, the
  incident already has alerts on the same resource, a resource this one depends on, or a resource
  that depends on this one.
- The incident's resource set grows as alerts join, so a ripple (db → api → frontend) becomes one
  incident.
- Every grouped alert stores a human-readable reason ("checkout-api depends on orders-db, already
  in INC-7"), shown in the UI. Grouping is never a black box.
- The **probable origin** is the involved resource that depends on nothing else in the incident.
  It's labelled a hint, not a verdict.

**Coupling**
- The incidents module listens for `AlertFired` / `AlertResolved` events. The alerts module doesn't
  know incidents exist, and `incident_alerts` copies what it needs instead of joining alert tables.
- Listeners run *after* the alert transaction commits, in their own transaction
  (`@TransactionalEventListener` + `REQUIRES_NEW`), so an incident bug can't block alerting.
  Handling is idempotent (an alert already grouped is ignored).
- Gap: a crash between the two transactions would skip grouping for that alert. A transactional
  outbox (or a message broker) would close it; noted as future work.

**Lifecycle**
- `OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED`, one step at a time, enforced inside the
  `Incident` aggregate. Invalid moves return 409.
- Acknowledging assigns the incident to the acknowledger if nobody owns it.
- Resolving requires a written resolution. Alerts recovering does **not** auto-resolve: someone
  must confirm the fix (the UI prompts them when all alerts are green).
- Every command appends a timeline entry in the same transaction; `@Version` optimistic locking
  turns simultaneous edits into a 409 instead of a lost update.
- The timeline merges our entries with logs of involved resources and deployments of involved
  resources *and the services they call*.

## Consequences

- One root failure → one incident, with the reasoning visible.
- Strict transitions may feel rigid (no jumping from OPEN to RESOLVED for a false alarm); a
  "dismiss as noise" path can be added later with its own audit entry.
- Grouping quality depends on the dependency graph being correct, which is also true for any
  human doing the same job.
