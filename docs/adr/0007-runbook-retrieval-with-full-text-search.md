# ADR-0007: Retrieve runbooks with PostgreSQL full-text search first, vectors later

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

The AI assistant (M7) must answer from the team's runbooks, not from its own memory. That needs a
retrieval step: given an incident's evidence, find the runbook sections that apply. The design
doc assumed embeddings in pgvector. Looking at the actual queries changed the plan.

What we search with is alert messages, resource names and log lines such as
`too many clients already`, `HikariPool-1`, `OOMKilled (exit code 137)`. These are exact,
rare tokens. Runbooks quote the same tokens in their Symptoms sections.

| Option | Pros | Cons |
|---|---|---|
| **PostgreSQL full-text search** (`tsvector`, GIN, `ts_rank_cd`) | Excellent on exact error strings; no API key, no cost, no network call; deterministic, so retrieval quality is a normal unit test; nothing new to run | Misses paraphrases ("database refuses logins" vs "too many clients") |
| Embeddings in pgvector | Finds paraphrases and concepts | Needs an embedding API (key, cost, latency, an outage dependency during incidents); weaker on rare exact tokens; results change with the model, so tests are fuzzy |
| Hybrid (both, merged ranks) | Best quality | Most moving parts; only worth it once full-text is measurably not enough |

## Decision

- Split each runbook by Markdown heading into chunks that keep their heading path
  ("Orders DB connection exhaustion > Verify"), at most 1,200 characters each.
- Store a generated `tsvector` per chunk: heading words weighted **A**, body words **B**.
- Build queries in Java from plain alphanumeric words only, joined with OR, capped at 30 terms.
  Nothing from user or log text can reach `to_tsquery` as an operator.
- Rank with `ts_rank_cd`; return at most two chunks per runbook so one long runbook can't fill
  the results.
- For an incident, the query text is its alert messages, affected resource names and the last 20
  error log lines.
- Snippets mark matches as `[[word]]`; the UI renders them as text, never HTML.
- A retrieval evaluation test asserts that, for each of the four failure scenarios, the correct
  runbook ranks first when searching with that scenario's evidence only.
- pgvector stays enabled (migration V1). Embeddings can be added as a second ranker behind the
  same `KnowledgeService.search` method without changing callers.

## Consequences

- Retrieval works offline, is free, and is covered by deterministic tests.
- Paraphrased questions typed by a human ("why are logins failing?") may miss. If the evaluation
  set grows and shows that, add embeddings and merge the two rankings (reciprocal rank fusion).
- Known limit: PostgreSQL's `ts_headline` strips angle-bracket placeholders such as `<service>`
  from snippets. The full runbook page shows them correctly.
