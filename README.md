# CloudPilot

[![CI](https://github.com/sudhanshushekar898/cloudpilot/actions/workflows/ci.yml/badge.svg)](https://github.com/sudhanshushekar898/cloudpilot/actions/workflows/ci.yml)

**Cloud monitoring and incident management with AI-assisted root-cause analysis.**

CloudPilot watches the health of cloud resources (EC2, RDS, APIs, queues, Kubernetes pods),
raises alerts when metrics break their thresholds, groups related alerts into incidents, and
helps on-call engineers work out *what failed, why, and what to do next*.

The AI assistant answers questions like *"Why did checkout fail after 2 PM?"* using only the
incident's own evidence (metrics, logs, deployment events) and the team's runbooks. It
recommends safe remediation steps but **never runs commands against infrastructure**.

> Status: early development (M9 of 11 done). See the [roadmap](#roadmap) below.

## Getting started

### Run everything with Docker

Requires Docker Desktop.

```bash
docker compose up --build
```

| Service | URL |
|---|---|
| Web app | http://localhost:3000 |
| API ping | http://localhost:8080/api/v1/ping |
| Grafana dashboard | http://localhost:3001 (read-only without login) |
| Prometheus | http://localhost:9090 |

### Run for development

Requires Java 21+, Node 22+ and Docker (for PostgreSQL).

```bash
docker compose up -d db
cd backend && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

The Vite dev server runs on http://localhost:5173 and proxies `/api` to the backend.

### Demo accounts

Created automatically on startup (disable with `DEMO_SEED_USERS=false`):

| Role | Email | Password |
|---|---|---|
| Admin | admin@cloudpilot.dev | CloudPilot#2026 |
| SRE | sre@cloudpilot.dev | CloudPilot#2026 |
| Developer | dev@cloudpilot.dev | CloudPilot#2026 |

The login page has a button for each one.

## Simulated environment

CloudPilot monitors a simulated e-commerce checkout system, so the demo is free and repeatable:

```
web-frontend (EC2) ──> checkout-api (API) ──> orders-db (RDS)
                  └──> catalog-api (API)  └──> payments-queue (QUEUE) <── payments-worker (POD)
                           └──> inventory-pod (POD)        payments-worker ──> orders-db
```

Every 10 seconds the simulator writes CPU, memory, p95 latency, error rate and availability
for each resource: a typical baseline for its type plus random noise. On first start it
backfills one hour of history so the charts aren't empty.

Admins can start a failure scenario from the dashboard. Each one hits the root cause hardest,
and the resources that depend on it show knock-on symptoms:

| Scenario | Root cause | Knock-on effects | Duration |
|---|---|---|---|
| DB connection pool exhausted | orders-db CPU, latency, errors | checkout-api slow and failing, web-frontend errors | 15 min |
| Bad checkout-api deployment | checkout-api 38% errors, 70% availability | web-frontend errors | 10 min |
| Payments queue backlog | payments-queue message age grows | payments-worker CPU, checkout-api latency | 15 min |
| Memory leak in payments-worker | memory grows steadily | availability drops as pods restart | 20 min |

Resource status on the cards comes from the latest values: **Down** when availability is
below 90%, **Degraded** when error rate ≥ 5%, p95 latency ≥ 1 s, CPU or memory ≥ 90%, or
availability below 99%.

## Alerts

Alert rules look like *"API error rate above 5% for 5 minutes"*. Every 30 seconds the alert
engine checks each enabled rule against every resource in its scope:

- **Fires** only if *every* sample in the window breaches the threshold and the samples cover the
  whole window. A short spike, or two minutes of bad data, doesn't fire a 5-minute rule.
- **Resolves** only after a full minute with every sample back to normal (no flapping).
- **Never duplicates:** one firing alert per rule and resource, enforced by a partial unique index.
- **No data never resolves:** a resource that stops reporting hasn't recovered.

Ten default rules ship with the app (API errors, availability, latency, DB CPU and errors, queue
age, pod memory/CPU/errors, frontend errors). Admins can create, edit and disable rules on the
**Alert rules** page. Details: [ADR-0005](docs/adr/0005-alert-evaluation-semantics.md).

## Incidents

Alerts are grouped into incidents so one failure pages once, not five times:

- A new alert joins an active incident if, within 10 minutes, that incident already has alerts on
  the **same resource**, a resource it **depends on**, or a resource that **depends on it**. A
  ripple like `orders-db → checkout-api → web-frontend` becomes one incident.
- Every grouped alert shows **why** it was grouped, and the incident marks the **probable
  origin**: the affected resource that depends on nothing else in the incident.
- Lifecycle: **Open → Acknowledged → Investigating → Resolved**, one step at a time (409 otherwise).
  Acknowledging takes ownership; resolving needs a written resolution. Only SREs and admins can
  change state; anyone can comment.
- The **timeline** merges alerts, status changes, comments, error logs of the affected resources
  and recent deployments (including services they call), with filters by type and resource.

Details: [ADR-0006](docs/adr/0006-incident-grouping-and-lifecycle.md).

## Runbooks (knowledge base)

Runbooks are the team's written knowledge, and the only source the AI assistant will be allowed to cite.

- Five runbooks ship with the app (DB connection exhaustion, bad deployment and rollback, queue
  backlog, pod memory leak, API latency triage). Admins can add more by pasting or uploading Markdown.
- Each runbook is split by heading into sections that keep their path, e.g.
  *Orders DB connection exhaustion > Verify*.
- Search uses PostgreSQL full-text search (`tsvector` + GIN index, heading words weighted higher).
  It needs no API key and gives the same result every time, so retrieval quality is tested like any other code.
- Every incident shows **suggested runbooks**, found by searching with the incident's alert
  messages, affected resources and recent error logs.
- A retrieval evaluation test checks that the right runbook ranks first for every failure scenario.

Why not embeddings yet: [ADR-0007](docs/adr/0007-runbook-retrieval-with-full-text-search.md).

## AI root-cause analysis

On any incident, ask a question such as *"Why did checkout fail?"*. The answer has a fixed shape:
**observed evidence**, **likely cause**, **confidence**, **read-only verification steps** and
**safe remediation steps**, and every statement links to the evidence behind it.

- **Evidence only.** The analysis sees the incident's alerts (A1…), distinct error logs (L1…),
  recent deployments (D1…) and the best-matching runbook sections (R1…). Nothing else.
- **Claude when configured.** With `ANTHROPIC_API_KEY` set, `claude-opus-5-5` answers through the
  official Anthropic Java SDK using structured outputs. Without a key, a deterministic rule-based
  analyser answers. If Claude errors, times out or declines, the rule-based answer is shown with a note.
- **Guardrails on every answer.** Citations to evidence that doesn't exist are removed, an unsupported
  cause gets its confidence capped at 30%, and steps that delete, terminate or purge are marked
  **Needs approval**.
- **Never executes anything.** CloudPilot has no way to run commands; a person verifies and acts.
- **Prompt-injection aware.** Log text is escaped and marked as untrusted data in the prompt.
- Each answer is stored with the exact evidence it was given, for later review.

```bash
# Optional: enable Claude (rule-based analysis is used without it)
ANTHROPIC_API_KEY=your-key docker compose up -d
```

Design and trade-offs: [ADR-0008](docs/adr/0008-ai-root-cause-analysis.md).

## Postmortems and reports

**Postmortems.** Once an incident is resolved, an SRE clicks **Draft postmortem**. CloudPilot drafts
the summary, impact, root cause, resolution and prevention actions (with Claude if configured,
otherwise from a template) and builds the **timeline from recorded events**, which nobody can edit.
An SRE or admin edits the draft and **approves** it; approval records who approved it and locks the
document. The root-cause draft quotes the incident's analysis and asks a person to confirm it.

**Reports.** The Reports page shows, for the last 7, 30 or 90 days:

| Metric | Definition |
|---|---|
| MTTA | mean time from incident opened to acknowledged |
| MTTR | mean time from incident opened to resolved (from the first alert) |
| SLA compliance | share of resolved incidents within target: critical 60 min, warning 4 h, info 24 h |
| Recurring failures | same resource and same alert once numbers are removed, at least twice |
| Responders | incidents resolved and mean time to resolve per person |

Details: [ADR-0009](docs/adr/0009-postmortems-and-reliability-metrics.md).

## Monitoring CloudPilot itself

- **Metrics:** Prometheus scrapes the backend every 15 s on a private management port (8081, not
  published). Besides HTTP and JVM metrics there are product metrics: alerts fired and grouped,
  incidents opened and active, time to resolve, AI analysis latency and fallbacks, alert-engine run
  time, failed logins.
- **Dashboard:** Grafana's *CloudPilot overview* (provisioned from `ops/grafana`) shows request rate,
  5xx ratio, p50/p95/p99 latency, active incidents, alerts, AI analysis and JVM heap.
- **Logs:** JSON (Elastic Common Schema) in Docker. Every request gets an `X-Request-Id` (sent back in
  the response), and every log line for that request includes it as `requestId`.
- **Alerts:** five Prometheus rules in `ops/prometheus/alerts.yml`, checked by `promtool` in CI.
  Details: [ADR-0010](docs/adr/0010-observability.md).

## Operating CloudPilot (runbook)

Each Prometheus alert links to one of these sections.

### CloudPilot down
`up{job="cloudpilot"} == 0` for 1 minute.
1. `docker compose ps`: is `backend` running and healthy?
2. `docker compose logs backend --tail 100`: look for startup errors (database unreachable, a
   failed Flyway migration, the prod profile refusing a default secret).
3. Check the database: `docker compose ps db`. The backend won't start without it.
4. Restart only the backend: `docker compose up -d backend`. Simulated metrics resume; alert rules
   re-evaluate from stored data, so nothing is lost.

### High error rate
More than 5% of API requests return 5xx for 5 minutes.
1. Grafana → *API requests by status*: which status code, since when?
2. Take a `requestId` from a failing response (or from the logs) and search the backend logs for it.
3. Common causes: database connection errors (check `db`), a bad deploy (roll back the image).

### Slow API
p95 latency above 1 s for 5 minutes.
1. Grafana → *API latency*: all endpoints or one? Check JVM heap for memory pressure.
2. Slow AI analysis does not count here (it has its own metric), but heavy report or timeline queries can.
3. Check the database for long-running queries: `SELECT pid, now() - query_start, query FROM pg_stat_activity WHERE state <> 'idle';`

### Slow alert engine
An evaluation run takes over 20 s of its 30 s interval.
1. Usually too many rules or resources, or slow `metric_samples` queries.
2. Check the retention job ran (samples older than 7 days should be gone) and that the
   `metric_samples_lookup` index exists.

### AI falling back
More than 3 analyses in 30 minutes fell back to the rule-based analyser.
1. Answers still work (rule-based). Look in the logs for `LLM analysis failed` or `refused`.
2. Failures: check the API key, rate limits and network access to the Claude API. Refusals: check
   the evidence for unusual content in log lines.

## API

| Method | Path | Access | Description |
|---|---|---|---|
| GET | `/api/v1/ping` | public | Liveness check, no database |
| GET | `/actuator/health` | public | Health including database |
| POST | `/api/v1/auth/login` | public | Email + password → JWT (1 hour) |
| GET | `/api/v1/auth/me` | signed in | Current user from the token |
| GET | `/api/v1/users` | Admin | List users |
| GET | `/api/v1/resources` | signed in | All resources with status and latest metrics |
| GET | `/api/v1/resources/{id}` | signed in | One resource |
| GET | `/api/v1/resources/{id}/metrics?minutes=60` | signed in | Bucketed history (1–1440 min, ≤120 points per metric) |
| GET | `/api/v1/simulator` | signed in | Scenario catalogue and the running scenario |
| POST | `/api/v1/simulator/scenarios/{type}/start` | Admin | Start a failure scenario |
| POST | `/api/v1/simulator/stop` | Admin | Stop the running scenario |
| GET | `/api/v1/alerts?status=FIRING` | signed in | Alerts, newest first (optional status filter) |
| GET | `/api/v1/alerts/summary` | signed in | Count of firing alerts by severity |
| GET | `/api/v1/alert-rules` | signed in | All alert rules |
| POST | `/api/v1/alert-rules` | Admin | Create a rule (409 on duplicate name) |
| PUT | `/api/v1/alert-rules/{id}` | Admin | Update or enable/disable a rule |
| GET | `/api/v1/incidents?scope=active` | signed in | Incidents (`active`, `resolved`, `all`) |
| GET | `/api/v1/incidents/summary` | signed in | Number of active incidents |
| GET | `/api/v1/incidents/{id}` | signed in | Detail: grouped alerts, reasons, probable origin |
| GET | `/api/v1/incidents/{id}/timeline?type=LOG&resourceId=` | signed in | Merged, filterable timeline |
| POST | `/api/v1/incidents/{id}/acknowledge` | SRE, Admin | Take ownership |
| POST | `/api/v1/incidents/{id}/investigate` | SRE, Admin | Start investigating |
| POST | `/api/v1/incidents/{id}/resolve` | SRE, Admin | Resolve with a resolution note |
| POST | `/api/v1/incidents/{id}/assign` | SRE, Admin | Assign to an SRE or admin |
| POST | `/api/v1/incidents/{id}/comments` | signed in | Add a comment |
| GET | `/api/v1/users/assignable` | SRE, Admin | People an incident can be assigned to |
| GET | `/api/v1/runbooks` | signed in | All runbooks |
| GET | `/api/v1/runbooks/{slug}` | signed in | One runbook (Markdown) |
| GET | `/api/v1/runbooks/search?q=` | signed in | Ranked sections with highlighted snippets |
| GET | `/api/v1/runbooks/suggestions?incidentId=` | signed in | Sections relevant to an incident |
| POST | `/api/v1/runbooks` | Admin | Add a Markdown runbook (409 on duplicate title) |
| DELETE | `/api/v1/runbooks/{slug}` | Admin | Delete a runbook |
| POST | `/api/v1/incidents/{id}/analysis` | signed in | Ask a question; returns a cited analysis |
| GET | `/api/v1/incidents/{id}/analysis` | signed in | Latest analysis for the incident |
| GET | `/api/v1/incidents/{id}/postmortem` | signed in | The postmortem with its fact-based timeline |
| POST | `/api/v1/incidents/{id}/postmortem/generate` | SRE, Admin | Draft (or redraft) after resolution |
| PUT | `/api/v1/incidents/{id}/postmortem` | SRE, Admin | Edit the draft |
| POST | `/api/v1/incidents/{id}/postmortem/approve` | SRE, Admin | Approve and lock |
| GET | `/api/v1/reports/summary?days=30` | signed in | MTTA, MTTR, SLA, recurring failures, responders |

Errors use [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457):

```json
{ "title": "Authentication failed", "status": 401, "detail": "Invalid email or password" }
```

### Security design

- Passwords hashed with BCrypt; tokens signed with HS256 via Spring Security's JWT support.
- Same error and similar timing for unknown email vs wrong password (no user enumeration).
- Stateless: no server sessions, token sent in the `Authorization` header (no CSRF surface).
- The `prod` profile refuses to start with the development JWT secret or demo password.
- Details and trade-offs: [ADR-0003](docs/adr/0003-stateless-jwt-auth.md).

### Run the tests

```bash
cd backend && ./mvnw verify      # unit + integration tests (Testcontainers) + coverage gate
cd frontend && npm test          # component tests (Vitest + Testing Library)
```

## Project structure

```
cloudpilot/
├── backend/            Spring Boot API (modular monolith, package dev.cloudpilot)
├── frontend/           React + TypeScript + Vite web app
├── docs/               Design doc, ADRs, user stories, dev log
├── docker-compose.yml  Local stack: Postgres (pgvector), API, web app
└── .github/workflows/  CI pipeline
```

## Testing strategy

| Level | Tools | What it covers |
|---|---|---|
| Unit / slice | JUnit 5, MockMvc, AssertJ | Controllers and services in isolation, with a fixed clock |
| Integration | Testcontainers (real PostgreSQL + pgvector) | Flyway migrations, health checks, real HTTP calls |
| Frontend | Vitest, React Testing Library | Components, with `fetch` mocked |
| Coverage | JaCoCo | Build fails below 80% line coverage |

CI runs all of these on every pull request, then builds both Docker images.

## Features (planned)

- JWT login with role-based access: **Admin**, **SRE**, **Developer**
- Live dashboard of simulated cloud resources with CPU, memory, latency, error-rate and availability charts
- Alert rules such as *"API error rate > 5% for 5 minutes"*
- Incident lifecycle: open → acknowledged → investigating → resolved
- Incident timeline with correlated alerts, logs and deployment events
- RAG-based AI root-cause analysis with citations and a confidence score
- AI-drafted postmortems that a human edits and approves
- MTTR, SLA and recurring-failure reports

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React, TypeScript, Vite, Tailwind CSS, Recharts |
| Backend | Java 21, Spring Boot 3, Spring Web, Spring Data JPA |
| Security | Spring Security, JWT, RBAC |
| Database | PostgreSQL, Flyway |
| AI | LLM API with retrieval over runbooks and incident history |
| Observability | Micrometer, Prometheus, Grafana |
| Delivery | Docker Compose, GitHub Actions |

## Roadmap

| Milestone | Scope |
|---|---|
| M0 | Repository setup, design doc, user stories |
| M1 | Runnable skeleton: API, web app, Docker, CI |
| M2 | Authentication and roles |
| M3 | Resources and metric simulator |
| M4 | Alert rules engine |
| M5 | Incident lifecycle and timeline |
| M6 | Runbook knowledge base (retrieval) |
| M7 | AI root-cause analysis |
| M8 | Postmortems and analytics |
| M9 | Observability |
| M10 | Test hardening and load testing |
| M11 | Deployment and release |

## Documentation

- [Design document](docs/design.md)
- [Architecture decision records](docs/adr)
- [User stories](docs/user-stories.md)
