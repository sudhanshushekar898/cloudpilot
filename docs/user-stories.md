# User Stories

Format: *As a [role], I want [goal], so that [reason].* Each story lists its acceptance
criteria (AC). Stories are tracked as GitHub issues and tagged with their milestone.

## M1 — Runnable skeleton

**US-01 Health check**
As an engineer, I want a health endpoint, so that I can tell whether the API is up.
- AC1: `GET /api/v1/ping` returns `200` with `{"status":"ok"}`.
- AC2: `GET /actuator/health` returns `UP` when the database is reachable.
- AC3: The web app shows "API online" or "API offline" based on the ping.

## M2 — Authentication and roles

**US-02 Login**
As a user, I want to log in with email and password, so that only my team can see incidents.
- AC1: Valid credentials return a JWT that expires after 1 hour.
- AC2: Wrong credentials return `401` with a generic message (no hint whether the email exists).
- AC3: Passwords are stored as BCrypt hashes.

**US-03 Role-based access**
As an admin, I want actions limited by role, so that developers can't close incidents by mistake.
- AC1: A Developer calling `POST /incidents/{id}/resolve` gets `403`.
- AC2: An SRE can acknowledge, assign and resolve incidents.
- AC3: Only an Admin can create or edit alert rules.

## M3 — Resources and simulator

**US-04 Resource dashboard**
As an SRE, I want to see every resource with its current status, so that I spot problems at a glance.
- AC1: Each card shows name, type (EC2, RDS, API, QUEUE, POD) and status (healthy / degraded / down).
- AC2: Clicking a card shows CPU, memory, latency, error rate and availability for the last hour.

**US-05 Failure scenarios**
As a demo user, I want to trigger a realistic failure, so that I can watch CloudPilot detect it.
- AC1: An Admin can start a scenario such as "database connection pool exhausted".
- AC2: The scenario changes metrics and logs of the affected resource and its dependents.

## M4 — Alerts

**US-06 Alert rules**
As an admin, I want to define rules like "error rate > 5% for 5 minutes", so that alerts match our SLOs.
- AC1: A rule has metric, operator, threshold, duration and severity.
- AC2: An alert fires only when the condition holds for the full duration.
- AC3: The same rule does not fire twice for the same resource while an alert is open.

## M5 — Incidents

**US-07 Alert grouping**
As an on-call engineer, I want related alerts grouped into one incident, so that I'm not flooded.
- AC1: Alerts on resources that depend on each other within 10 minutes join the same incident.
- AC2: The incident shows which alerts it contains and why they were grouped.

**US-08 Incident lifecycle**
As an SRE, I want to acknowledge, investigate and resolve incidents, so that the team knows who owns what.
- AC1: Allowed transitions: OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED.
- AC2: Invalid transitions return `409`.
- AC3: Every change is added to the incident timeline with who did it and when.

**US-09 Timeline**
As an engineer, I want a timeline of alerts, logs and deployments, so that I can see what changed first.
- AC1: Entries are sorted by time and filterable by type and resource.

## M6–M7 — Knowledge base and AI

**US-10 Runbooks**
As an admin, I want to upload Markdown runbooks, so that the AI can use our team's knowledge.

**US-11 Root-cause analysis**
As an engineer, I want to ask "why did checkout fail after 2 PM?", so that I get a starting point fast.
- AC1: The answer has: observed evidence, likely cause, confidence, verification steps, safe remediation.
- AC2: Every claim cites a log, metric, deployment or runbook section.
- AC3: The answer never contains an action that CloudPilot runs by itself.

## M8 — Postmortems and reports

**US-12 Postmortem draft**
As an SRE, I want a postmortem draft after resolving an incident, so that writing it takes minutes, not hours.
- AC1: Draft has impact, timeline, root cause, resolution and prevention actions.
- AC2: It stays a draft until an SRE approves it.

**US-13 Reports**
As a team lead, I want MTTR, SLA and recurring-failure reports, so that I can see if we're improving.
