# ADR-0004: Store metrics in PostgreSQL, accessed with plain JDBC

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

The simulator writes 5 metrics × 7 resources every 10 seconds: about 3,000 rows an hour,
about 25,000 rows a day. The app needs three reads: the latest value per resource and metric
(dashboard), averaged time buckets for a range (charts), and later, windows for alert rules.

Options considered:

| Option | Pros | Cons |
|---|---|---|
| **PostgreSQL table + JDBC** | No new infrastructure; SQL has `DISTINCT ON` and `date_bin` for exactly these reads; testable with Testcontainers | Not built for millions of series; we must handle retention ourselves |
| Prometheus as the store | Built for metrics, has PromQL | Another service to run; awkward to join with resources and incidents; harder to seed scenarios |
| TimescaleDB extension | Postgres + automatic partitioning and compression | Different image; not needed at this volume |
| JPA entity for samples | Consistent with the rest of the code | Can't batch inserts with a `BIGSERIAL` key (Hibernate must read back each id); aggregations become awkward JPQL or native queries anyway |

## Decision

- Samples live in one PostgreSQL table, `metric_samples`, indexed on
  `(resource_id, metric, recorded_at DESC)`.
- `MetricStore` uses `JdbcTemplate`: `batchUpdate` for inserts, `DISTINCT ON` for latest values,
  `date_bin` + `avg` for chart buckets (at most 120 points per metric, whatever the range).
- A nightly job deletes samples older than 7 days.
- Everything else (resources, users, later incidents) stays on JPA, where entities and
  relationships pay off.

## Consequences

- One database, one backup, simple joins between metrics, resources and incidents.
- Charts stay fast because the database aggregates; the browser never receives raw samples.
- At much higher volume (thousands of resources), the next step is TimescaleDB hypertables
  with compression, or moving metrics to Prometheus and keeping only incidents in Postgres.
  `MetricStore` is the only class that would change.
