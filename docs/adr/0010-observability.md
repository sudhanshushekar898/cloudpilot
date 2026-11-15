# ADR-0010: Observability: Prometheus metrics, JSON logs with request ids, Grafana

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

CloudPilot watches other systems; something has to watch CloudPilot. When the alert engine slows
down or the AI provider keeps failing, the on-call engineer needs to know before users do, and
needs to find the log lines behind a reported error quickly.

## Decision

**Metrics:** Micrometer with the Prometheus registry, scraped every 15 s.
- HTTP server metrics with **histogram buckets**, so p95/p99 can be computed in Prometheus and
  aggregated across instances (client-side percentiles can't be averaged).
- Domain metrics from the modules that own the events:

  | Metric | Why |
  |---|---|
  | `cloudpilot_alerts_fired_total{severity,metric}` | alert volume; spikes mean noise or a real outage |
  | `cloudpilot_alerts_grouped_total` | pages saved by grouping (the product's main promise) |
  | `cloudpilot_incidents_opened_total{severity}`, `cloudpilot_incidents_active` | workload |
  | `cloudpilot_incidents_time_to_resolve_seconds{severity}` | live MTTR |
  | `cloudpilot_analysis_duration_seconds{analyzer,outcome}` | AI latency and fallback rate |
  | `cloudpilot_alerts_engine_run_seconds` | how close evaluation is to overlapping its 30 s interval |
  | `cloudpilot_auth_login_failed_total` | brute-force signal |

- **Private management port.** In Docker, actuator (health and metrics) listens on 8081, which is
  not published; nginx forwards only `/api`. The metrics endpoint therefore needs no token.

**Alerts:** five Prometheus rules (down, 5xx > 5%, p95 > 1 s, slow engine, AI falling back), each
linking to a runbook section in the README. Validated with `promtool` in CI. Alertmanager
(routing to email or a pager) is out of scope locally.

**Logs:** Spring Boot's built-in structured logging (Elastic Common Schema JSON) in Docker; plain
text locally. A servlet filter gives each request an id (from `X-Request-Id` if it's short and
plain, otherwise a UUID), puts it in the MDC so every log line carries it, and returns it in the
response, even for 401/403. Failed logins are logged without the email.

**Dashboards:** Grafana with the datasource and an overview dashboard provisioned from files in
the repository, so they are versioned and reviewed like code.

## Consequences

- One request id connects a user's error report, the response header and the log lines.
- Dashboards and alert rules can't drift from what's in Git; CI rejects invalid rules.
- No distributed tracing yet. With one service it adds little; if services are split out,
  OpenTelemetry tracing would be the next step and the request id would become a trace id.
