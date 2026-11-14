# CloudPilot Dev Log

My notes while building CloudPilot: what I built, what broke, what I learned, and the
questions an interviewer could ask me about it. Newest entry on top.

<!--
Template for each entry (copy it for every milestone):

## YYYY-MM-DD — Mx: <title>

**Built:** one or two lines.

**Decisions and why:**
-

**What broke / what confused me:**
-

**What I learned:**
-

**Interview questions I can now answer:**
- Q:
  A:
-->

## 2026-11-14 — M0: Project setup and design

**Built:** GitHub repo, README, design doc (problem, scope, architecture, data model, API),
two ADRs, user stories with acceptance criteria, definition of done.

**Decisions and why:**
- Modular monolith instead of microservices (ADR-0001). The app has a few users and a few
  hundred metric samples per second. Microservices would add network calls, more deployments
  and distributed transactions without solving a real problem. Modules with clear boundaries
  give me the same separation, and I can still pull one out later if it needs to scale alone.
- AI only recommends, never executes commands (ADR-0002). An LLM can be confidently wrong, and
  a log line could contain text that tricks it (prompt injection). If it could run commands,
  one bad answer during an outage could make the outage worse. So it gives steps, a human runs them.
- Simulated AWS resources instead of a real account. Free, no risk of a surprise bill, and the
  same failure can be replayed every time I demo it. The ingestion side is designed so real
  metrics could replace the simulator later.

**What broke / what confused me:**
- The Mermaid architecture diagram had the same id (`API`) for a node and a subgraph, which
  breaks rendering. Fixed by pointing the arrow at the subgraph directly.
- Had to understand the difference between a design doc (what and how) and an ADR (one
  decision, the options, and why I picked one).

**What I learned:**
- An ADR records *why* a decision was made, so later nobody has to guess or repeat the debate.
- Acceptance criteria turn a user story into something testable: each AC becomes at least one test.
- Writing the out-of-scope list early stops the project from growing forever.

**Interview questions I can now answer:**
- Q: Why didn't you use microservices?
  A: The load doesn't need it, and microservices bring network failures, separate deploys and
  data consistency problems. I built a modular monolith with enforced module boundaries, so
  the code is already split the way services would be. If alert ingestion ever needs to scale
  on its own, I can extract it.
- Q: How do you stop the AI from giving dangerous advice?
  A: The AI has no way to execute anything. It only sees the incident's own evidence and
  runbook sections, must answer in a fixed structure with citations, and answers without
  valid citations are marked low confidence. Logs are passed as untrusted data, and a human
  always decides what to run.
