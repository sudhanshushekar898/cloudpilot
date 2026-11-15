# ADR-0011: Deployment on one EC2 VM with Docker Compose, SHA-tagged images and kill switches

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

CloudPilot needs a public, always-on demo with HTTPS, on a student budget, and a way to undo a
bad release in minutes. The load is tiny (a handful of users, a few hundred samples a second).

## Decision

- **One `t3.small` EC2 instance running `deploy/docker-compose.prod.yml`**: PostgreSQL, backend,
  web app, Caddy, Prometheus and Grafana. Only Caddy publishes ports (80/443).
- **Caddy** terminates TLS with automatic Let's Encrypt certificates for `<ip>.sslip.io`, so no
  domain purchase is needed. Actuator is never routed publicly.
- **Immutable images tagged with the commit SHA**, published to GHCR only after CI passes on
  `main`. A deploy is "run SHA x"; a rollback is "run the SHA before it".
- **`deploy/deploy.sh`** checks out the same commit (so config matches the images), waits for the
  backend health check and rolls back automatically if it never turns healthy. It keeps a
  release history for `--rollback`.
- **Manual deploy workflow** (`workflow_dispatch`, `production` environment) over SSH with a
  deploy-only key, followed by a smoke test against the public URL.
- **Runtime kill switches** in a `feature_flags` table: `claude-ai` and `failure-scenarios`.
  Unknown flag names read as off.
- **Forward-only, additive migrations** so the previous backend still runs on the newer schema.

## Alternatives considered

- **ECS Fargate + RDS:** managed and closer to a real company setup, but about 4-5x the cost
  (RDS alone exceeds the whole VM) and much more infrastructure for one small app.
- **Kubernetes (EKS or k3s):** rolling updates and self-healing, but the control plane costs
  money or memory and the complexity is not justified at this size.
- **Render / Railway:** easiest, but less to learn about Linux, TLS, networking and rollback.
- **Environment-variable feature flags:** need a restart to change, which defeats a kill switch.

## Consequences

- One machine is a single point of failure, and deploys have a few seconds of downtime while the
  backend restarts. Acceptable for a demo; the move to ECS + RDS is documented as the next step.
- Backups are my responsibility (pg_dump or EBS snapshots).
- Kill switches cost one primary-key lookup per analysis, which is negligible.
