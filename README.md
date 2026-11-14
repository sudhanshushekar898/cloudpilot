# CloudPilot

**Cloud monitoring and incident management with AI-assisted root-cause analysis.**

CloudPilot watches the health of cloud resources (EC2, RDS, APIs, queues, Kubernetes pods),
raises alerts when metrics break their thresholds, groups related alerts into incidents, and
helps on-call engineers work out *what failed, why, and what to do next*.

The AI assistant answers questions like *"Why did checkout fail after 2 PM?"* using only the
incident's own evidence (metrics, logs, deployment events) and the team's runbooks. It
recommends safe remediation steps but **never runs commands against infrastructure**.

> Status: early development. See the [roadmap](#roadmap) below.

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
