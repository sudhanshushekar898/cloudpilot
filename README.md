# CloudPilot

[![CI](https://github.com/sudhanshushekar898/cloudpilot/actions/workflows/ci.yml/badge.svg)](https://github.com/sudhanshushekar898/cloudpilot/actions/workflows/ci.yml)

**Cloud monitoring and incident management with AI-assisted root-cause analysis.**

CloudPilot watches the health of cloud resources (EC2, RDS, APIs, queues, Kubernetes pods),
raises alerts when metrics break their thresholds, groups related alerts into incidents, and
helps on-call engineers work out *what failed, why, and what to do next*.

The AI assistant answers questions like *"Why did checkout fail after 2 PM?"* using only the
incident's own evidence (metrics, logs, deployment events) and the team's runbooks. It
recommends safe remediation steps but **never runs commands against infrastructure**.

> Status: early development (M3 of 11 done). See the [roadmap](#roadmap) below.

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
| API health | http://localhost:8080/actuator/health |

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
