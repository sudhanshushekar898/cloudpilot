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

## 2026-11-15 — M8: Postmortems and reliability reports

**Built:** postmortems (Flyway V11) drafted by Claude or a template after an incident is resolved,
edited and approved by an SRE or admin, then locked; a timeline built only from recorded events; a
pure `ReportCalculator` for MTTA, MTTR, SLA compliance, recurring failures and responder stats; a
reports API. Frontend: a postmortem panel on resolved incidents (draft, edit, approve, read-only
view) and a Reports page with KPI tiles and tables.

**Decisions and why:**
- The timeline never comes from the model. It's the factual backbone of a postmortem, so it's
  built from what was recorded; the AI only drafts the narrative around it (ADR-0009).
- Human approval locks the document and records who approved it, the same idea as ADR-0002: the
  AI drafts, people decide.
- I generalised the Claude gateway to any structured output type (`complete(system, user, Class<T>)`)
  instead of writing a second client: postmortems reuse the same SDK setup, timeouts and fallbacks.
- The Claude gateway became an optional Spring bean, so features ask for it with `ObjectProvider`
  and work without a key.
- Every metric definition is written down and unit-tested. "MTTR" means different things in
  different teams; mine starts at the first alert and says so.
- "No data" instead of 0 when nothing was resolved: a 0-minute MTTR would look like a great result.
- Report tiles follow stat-tile rules: label, big number, plain ink colours. Status in the SLA table
  is an icon plus a word, never colour alone.

**What broke / what confused me:**
- `GET /postmortem` on an unresolved incident returned 409 instead of 404: I built the facts (which
  require resolution) before checking whether a postmortem existed. The order matters.
- The approve button saves the screen first (so what's approved is what you see); the test hadn't
  mocked that save, so approval looked broken.
- A login test failed at 5.2 s on a loaded machine (Vitest's default limit is 5 s). Raised the
  per-test limit to 15 s; fast tests are unaffected.
- The template wrote "…most likely cause. (from the analysis…)": the analysis text already ended with
  a full stop. Found it in the live run, fixed it test-first.
- `pgrep` doesn't exist in Git Bash on Windows, which made one of my wait loops misbehave.

**What I learned:**
- Blameless postmortems: describe systems and decisions, not people.
- MTTA vs MTTR vs time-to-detect, and why the definition has to be written down.
- Spring `ObjectProvider` for optional dependencies, and `@ConditionalOnExpression` for beans that
  only exist when a setting is present.

**Interview questions I can now answer:**
- Q: How do you stop an AI-written postmortem from inventing facts?
  A: The timeline is built from recorded events and can't be edited. The model only sees the facts,
  must use numbers from them, and a person edits and approves the draft. Approval locks it.
- Q: How do you calculate MTTR, and what's the catch?
  A: Mean of resolved minus opened for incidents resolved in the window. The catch is that it starts
  at detection; failures that took a long time to alert look better than they were. That's why
  alert durations are also a prevention item.
- Q: What does "recurring failure" mean in your system?
  A: Same first-affected resource and the same alert text with numbers removed, at least twice in the
  window, e.g. "orders-db cpu #% > #% for # min". Simple and explainable; it surfaces problems a
  team keeps patching instead of fixing.

## 2026-11-15 — M7: AI root-cause analysis

**Built:** a numbered evidence bundle per incident (alerts, distinct logs, all recent deployments,
retrieved runbook sections), a rule-based analyser, a Claude analyser (official Anthropic Java SDK,
`claude-opus-5-5`, structured outputs, effort high), an `AnalysisRunner` that falls back to the rule
-based answer on errors or refusals, guardrails that check every answer, JSONB storage of each answer
with its evidence (Flyway V10), and an ask/latest API. Frontend: an AI analysis panel on each incident
with the cause, a confidence bar, clickable citations, "Needs approval" badges and the evidence list.

**Decisions and why:**
- The model gets only the evidence bundle, and every item has an id it must cite (ADR-0008). That
  turns "trust me" into "check A2 and L1".
- The guardrails don't trust the model to follow the prompt. They check the output: fake citations are
  removed, an uncited cause loses confidence, destructive steps are flagged. They run on the rule-based
  answer too.
- A rule-based analyser that always works. It's the fallback when the API is down or refuses, the
  default without a key, and the baseline the LLM has to beat.
- Structured outputs instead of "please answer in JSON": the SDK derives the schema from a Java record,
  the API enforces it, and there's no fragile text parsing.
- All deployments go in the evidence, not only related ones, so the analysis can explicitly rule out
  the unrelated catalog-api release in the DB scenario.
- Log text is escaped and wrapped in `<untrusted_log>`. A log line can contain anything, including
  "ignore your instructions"; a test proves it can't break out of its element.
- The system prompt never changes between incidents, so it's a cacheable prefix.
- The Claude call sits behind a tiny `ClaudeGateway` interface, so everything else is tested with a fake.
- Same question within 30 s returns the stored answer: no duplicate cost from double-clicks.

**What broke / what confused me:**
- Docker Desktop stopped between sessions again; the integration tests and the stack both need it.
- A prompt test expected `checkout-api -> orders-db` but the code correctly escaped it to `-&gt;`.
- Adding the analysis panel broke five incident-page tests: their mock answered *every* URL starting
  with `/api/v1/incidents/i1`, including the new `/analysis` call, with the incident itself.
- I couldn't call the real Claude API without a key. The request shape and the JSON schema are
  tested offline (building the request runs the SDK's schema check), but answer quality isn't.

**What I learned:**
- Structured outputs with the Java SDK: `StructuredOutputConfig` + a record class, typed response.
- Claude Opus 5.5 always thinks; you tune depth with `effort`, not by turning thinking off.
- Handle `stop_reason: refusal` before reading the content.
- Prompt injection is defended in layers: escaping, labelling untrusted data, and validating output.

**Interview questions I can now answer:**
- Q: How do you stop the AI from hallucinating a root cause?
  A: It only sees a numbered evidence bundle and must cite ids for every claim. A validator removes
  citations that don't exist, drops uncited observations and caps the confidence of an uncited cause.
  The UI links each claim to its evidence.
- Q: What if the LLM is down, slow or refuses?
  A: One retry, a 120-second timeout, then the deterministic rule-based analysis is shown with a note.
  Refusals are checked explicitly. The incident page always gets an answer.
- Q: What stops the AI from running something dangerous?
  A: There is no execution path at all; the AI returns text. Destructive-looking steps are flagged
  for approval, and a person runs everything.
- Q: How would you measure whether Claude is better than the rule-based analyser?
  A: Use the four simulator scenarios as labelled cases (known root cause, known red herring), run both
  analysers, and score whether the cause names the right resource, cites valid evidence and avoids the
  red herring. That's the next step once there's an API key.

## 2026-11-15 — M6: Runbook knowledge base and retrieval

**Built:** runbooks and searchable chunks (Flyway V9), a Markdown chunker that keeps heading paths,
an injection-safe search query builder, full-text search with ranking and highlighted snippets,
five seeded runbooks, upload/delete for admins, runbook suggestions for incidents, and a retrieval
evaluation test. Frontend: runbooks list with search, runbook page (rendered Markdown), admin
upload form, and a "Suggested runbooks" panel on every incident.

**Decisions and why:**
- Full-text search before embeddings (ADR-0007). Our queries are exact error strings
  (`too many clients already`, `OOMKilled`), which keyword search is best at. It needs no API key,
  costs nothing and is deterministic, so I can test retrieval quality. I changed the original
  plan (pgvector embeddings) after looking at what the queries actually are.
- Chunk by heading and keep the path. "Verify" alone means nothing; "Orders DB connection
  exhaustion > Verify" can be cited.
- Heading words weigh more than body words (`setweight` A vs B).
- The `tsvector` is a generated column: PostgreSQL keeps it in sync, Java never writes it.
- At most two sections per runbook in results, so one long runbook can't crowd out the rest.
- Queries are built from plain words only. Log lines are untrusted text; they must not be able to
  inject `to_tsquery` operators.
- Snippets come back with `[[match]]` markers and are rendered as text. Runbooks are user content,
  so no HTML from them is ever rendered. `react-markdown` also ignores raw HTML by default.

**What broke / what confused me:**
- One frontend test failed about one run in three, only in the full suite. Cause: the default
  1-second timeout for `findBy`/`waitFor` was too tight when many test files run in parallel on a
  cold start, and some tests read the mocked request before the mutation had sent it. Fixed both:
  helpers now wait for the request, and the async timeout is 3 s.
- Seeded runbook slugs come from the title, not the file name, so my test expected the wrong slugs.
- `"for"` used up a slot in the capped query; added a small stop-word list in the builder.
- `ts_headline` strips `<service>` from snippets because it looks like an HTML tag. Known limit,
  written in the ADR.
- react-markdown pushed the main bundle to 459 kB; lazy-loading the runbook page brought it back to 340 kB.

**What I learned:**
- RAG is two separate problems: retrieval (finding the right text) and generation. Retrieval can
  be built and measured on its own, before any LLM is involved.
- PostgreSQL full-text search: `to_tsvector`, `to_tsquery`, `@@`, GIN indexes, `ts_rank_cd`, `ts_headline`.
- A flaky test is a bug in the test. Re-running until green hides it; find the race.

**Interview questions I can now answer:**
- Q: Why didn't you use a vector database for RAG?
  A: I looked at the queries first. They're exact error strings from logs and alerts, where keyword
  search beats embeddings, and it avoids an external API during an incident. I wrote an evaluation
  test: for each failure scenario the right runbook must rank first. If that set shows misses on
  paraphrased questions, I'd add embeddings in pgvector and merge rankings with reciprocal rank fusion.
- Q: How do you know your retrieval is any good?
  A: A test builds the query from each scenario's evidence (resources, metrics, log lines, never
  the title) and asserts the top hit. It runs in CI, so editing a runbook or the ranking can't
  silently make retrieval worse.
- Q: How did you handle prompt/query injection from logs?
  A: Log text is data. For search, only alphanumeric words reach the query, so operators can't be
  injected. For display, matches are marked with plain delimiters and rendered as text.

## 2026-11-15 — M5: Incidents, grouping and timeline

**Built:** log events and deployments (Flyway V7) written by the simulator during scenarios;
incidents, incident alerts and timeline entries (V8); a pure `AlertGrouper`; an `Incident`
aggregate with a strict state machine and optimistic locking; event listeners that open and grow
incidents; lifecycle, assignment and comment commands; list, detail (with probable origin) and a
merged, filterable timeline. Frontend: incidents list, incident detail with next-step actions,
assignment, resolution form, grouped alerts with reasons, timeline filters, comment box, nav badge.

**Decisions and why:**
- Group along the dependency graph plus a 10-minute window (ADR-0006). Time alone merges unrelated
  failures; resource alone splits one failure into many incidents.
- Each grouped alert stores a plain-English reason. If engineers can't see why alerts were
  grouped, they stop trusting the grouping.
- Alerts → incidents via events after commit, in a new transaction. Alert creation can never be
  blocked by an incident bug. Handling is idempotent because listeners can run twice.
- The `Incident` aggregate owns its rules (`acknowledge`, `resolve`...), so no service can skip a step.
- Alerts recovering doesn't auto-resolve the incident: a person confirms the fix and writes what
  fixed it, which is what the postmortem (M8) needs.
- `@Version` optimistic locking: two SREs clicking "acknowledge" at once get one success and one 409,
  not a silent overwrite.
- `UserDirectory` and `ResourceService.topology()` are the public faces of auth and resources; the
  incidents module never touches their repositories.
- The simulator's DB scenario records an unrelated catalog-api deployment just before the failure:
  a red herring the M7 AI analysis must not blame. Deployments of services an affected resource
  *calls* show in the timeline for the same reason.

**What broke / what confused me:**
- Assigning from the dropdown sent an empty user id. The select is controlled, so React resets
  its value right after the change event, and my mutation read `e.target.value` later inside a
  closure. Fix: copy the value into a variable first. A real bug caught by a test.
- A test couldn't find the grouping reason because "Why grouped: " and the reason were one text
  node. Gave the reason its own element (also nicer for styling).
- `@TransactionalEventListener` only fires inside a transaction by default. Events published
  outside one (tests, some code paths) would be silently dropped, so I set `fallbackExecution = true`.
- The incident number comes from a database sequence; `@Generated` tells Hibernate to read it back
  after insert instead of expecting Java to set it.

**What I learned:**
- Aggregates: put invariants inside the entity, not in services, so they can't be bypassed.
- Domain events keep modules decoupled; the cost is eventual consistency and a delivery gap that
  an outbox pattern would close.
- `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW` is how you react to committed data in Spring.

**Interview questions I can now answer:**
- Q: How does CloudPilot reduce alert noise?
  A: Alerts only fire when the condition holds for the full window, and related alerts are grouped
  into one incident using the dependency graph and a time window, with the reason shown for each.
  One DB failure that triggers five alerts becomes one incident with one owner.
- Q: What happens if two engineers acknowledge the same incident at the same time?
  A: The incident has a `@Version` column. Both read version 3; the first update makes it 4; the
  second update's `WHERE version = 3` matches no row, Hibernate throws, and the API returns 409 so
  the second person reloads.
- Q: Why not call the incident service directly from the alert engine?
  A: That couples alerts to incidents and puts both in one transaction, so an incident bug could
  stop alerts. With events, alerts don't know incidents exist and are committed first. The trade-off
  is a small delivery gap, which a transactional outbox would close.

## 2026-11-15 — M4: Alert rules engine

**Built:** `alert_rules` and `alerts` tables (Flyway V5) with ten default rules (V6), a pure
`RuleEvaluator`, a `windowStats` SQL query, an `AlertEngine` that runs every 30 s, `AlertFired` /
`AlertResolved` events, rule CRUD for admins, alert list and summary endpoints. Frontend: alerts
page (Firing / All tabs, durations), alert rules page with an admin form, firing-count badge in the nav.

**Decisions and why:**
- "For 5 minutes" means every sample in the window breaches *and* the data covers the window
  (ADR-0005). Averages would let a short spike fire; the coverage check stops 2 minutes of data
  from counting as 5.
- Resolve needs a full minute of normal values, so a struggling service doesn't flap.
- Missing data never resolves an alert. Silence is not recovery.
- Duplicate protection twice: the engine checks for a firing alert, and a partial unique index
  (`WHERE status = 'FIRING'`) makes duplicates impossible even if two runs overlap.
- The engine takes `now` as a parameter instead of reading the clock, so integration tests can
  insert samples at exact times and run the engine "five minutes later" without waiting.
- Alerts publish events instead of calling the incidents module. Alerts don't depend on incidents;
  M5 just adds a listener. That's how the modular monolith keeps its boundaries.
- Severity is copied onto the alert when it fires, so editing a rule later doesn't rewrite history.
- Shared `NotFoundException` / `ConflictException` → one place maps them to 404 / 409.

**What broke / what confused me:**
- The whole integration suite failed with `ExceptionInInitializerError` in the test base class.
  Docker Desktop had stopped, so Testcontainers couldn't start Postgres. A static initializer
  failure shows up as a confusing error in *every* test class.
- Vitest passed, but `npm run build` failed: Vitest strips types without checking them, and
  `tsc` found an implicit `any` in a test helper. CI runs `typecheck` separately for this reason.
- Deciding the "observed value" for the message: the window's min looks better for the rule
  but the latest value is what an engineer sees on the chart, so I used the latest.

**What I learned:**
- PostgreSQL partial indexes: a unique constraint that only applies to rows matching a `WHERE`.
- `(array_agg(value order by recorded_at desc))[1]` gets the latest value inside a `GROUP BY`.
- `@RecordApplicationEvents` lets a Spring test assert which events were published.

**Interview questions I can now answer:**
- Q: How do you avoid alert fatigue?
  A: Require the condition to hold for the full duration (not one sample, not an average), resolve
  only after sustained recovery, never duplicate an open alert, and (next milestone) group related
  alerts into one incident so one failure doesn't page five times.
- Q: What happens if two instances run the alert engine at once?
  A: The partial unique index rejects a second firing alert for the same rule and resource, so
  data stays correct. To avoid wasted work I'd add ShedLock so only one instance runs the job.
- Q: Why is the evaluator a separate pure class?
  A: All the tricky edge cases (coverage, touching the threshold, flapping, no data) become fast
  unit tests with no database or clock. The engine only does I/O around it.

## 2026-11-15 — M3: Resources, metrics and the failure simulator

**Built:** resources + dependency graph + metric samples (Flyway V3), a seeded 7-resource checkout
topology (V4), `MetricStore` on JDBC, health status per resource, a simulator that writes metrics
every 10 s and backfills an hour on startup, four failure scenarios that admins can start, a
nightly retention job. Frontend: live dashboard (cards sorted worst-first, health summary,
scenario panel) and a detail page with five Recharts charts, dependencies and a time-range picker.

**Decisions and why:**
- Plain JDBC for metrics, JPA for everything else (ADR-0004). Hibernate can't batch inserts into a
  `BIGSERIAL` table, and the reads are aggregations SQL is good at: `DISTINCT ON` for the newest
  value, `date_bin` + `avg` for chart buckets.
- The database aggregates, not the browser: a 24-hour chart gets 120 points per metric, not 8,640.
- Scenarios follow the dependency graph: the root cause gets hit hardest, its dependents show
  symptoms. That's the realistic "one failure, many alerts" problem the incident module solves next.
- Fixed UUIDs for seeded resources: docs, tests and demos can refer to `orders-db` reliably.
- Scenario state in memory (`AtomicReference`), not the database: it's demo state and there's one
  instance. With several instances it would move to Postgres or Redis.
- Card status (snapshot) is not the same as an alert (condition held for N minutes). One noisy
  sample changes a colour; it shouldn't page anyone. That's M4.
- TanStack Query for polling and caching instead of hand-written `useEffect` loops: refetch
  interval, shared cache between dashboard and detail page, loading/error states for free.
- Lazy-loaded the chart page: the main bundle went from about 680 kB to 309 kB.

**What broke / what confused me:**
- Mockito's strict stubs failed a test with `UnnecessaryStubbingException`: when recent data
  exists, the backfill never loads resources, so my stub was unused. The fix was to stub only
  in the tests that need it. The error actually confirmed the code skips work correctly.
- The first `docker compose up --build` didn't recreate the containers, so I tested old code and
  got a 404 on the new endpoint. Always check the container "Up" time after a rebuild.
- jsdom has no `ResizeObserver`, which Recharts' `ResponsiveContainer` needs; added a stub in the
  test setup.
- Random data in tests is a recipe for flaky tests. Fixed: a seeded `Random(42)`, plus a
  property-style test that runs 500 ticks and asserts every resource stays healthy.

**What I learned:**
- `DISTINCT ON (a, b) ... ORDER BY a, b, time DESC` is the Postgres way to get the latest row per group.
- `date_bin(interval, ts, origin)` buckets timestamps to any interval (Postgres 14+).
- Index design follows queries: `(resource_id, metric, recorded_at DESC)` serves both the latest
  value and range reads; a separate `recorded_at` index serves retention deletes.

**Interview questions I can now answer:**
- Q: Why not JPA for the metrics table?
  A: It's append-only and write-heavy. With an identity key Hibernate disables JDBC batching
  because it needs each generated id back, so 35 inserts become 35 round trips. `batchUpdate`
  sends them together. The reads are aggregations, which are clearer in SQL.
- Q: How would this scale to 10,000 resources?
  A: Partition `metric_samples` by time (or use TimescaleDB with compression), pre-aggregate
  1-minute rollups, keep raw data for a shorter window, and possibly move metrics to a TSDB like
  Prometheus. Only `MetricStore` would change because nothing else touches the table.
- Q: How do you test code that uses randomness?
  A: Inject the `Random` and seed it in tests, then test properties ("always healthy without a
  scenario", "percentages stay in 0–100") over many iterations instead of exact values.

## 2026-11-15 — M2: Authentication and roles

**Built:** `users` table (Flyway V2), BCrypt passwords, JWT login (`/api/v1/auth/login`),
`/auth/me`, admin-only `/users`, three roles with `@PreAuthorize`, demo users seeded on
startup, a prod startup safety check. Frontend: API client, auth context, login page with demo
buttons, route guard, layout with role badge and sign out.

**Decisions and why:**
- Spring Security's resource server + Nimbus instead of a JWT library like jjwt: validation
  (signature, expiry, issuer) is done by battle-tested framework code, not by me.
- HS256 with one secret: the API issues and validates its own tokens. RS256 would only help if
  other services needed to verify them (ADR-0003).
- Same 401 message for unknown email and wrong password, and a dummy BCrypt check for unknown
  emails so the response time doesn't leak which emails exist.
- Role lives in the token, so `/auth/me` needs no database call. Downside: a role change only
  takes effect when the user's token expires (max 1 hour).
- The React route guard is only UX. Real protection is the backend rejecting requests.
- `ProductionSafetyCheck` crashes startup in prod if the dev secret is still set. A failed
  deploy is safer than a running app where anyone can forge tokens.

**What broke / what confused me:**
- Adding Spring Security made every endpoint require login, including `/ping`. `@WebMvcTest`
  slices also don't load my `SecurityConfig`, so I had to `@Import` it into the test.
- My `/users` RBAC test failed with a Jackson error: the endpoint returns a JSON *array* and the
  test read it as a `Map`. Bug in the test, not the code.
- Setting `JWT_SECRET=` (empty) in Compose didn't fall back to the `application.yml` default.
  Spring treats an empty env var as a real value. Moved the dev default into Compose.
- The demo user seeder put users into the test database and clashed with tests that create
  their own users. Disabled seeding for integration tests.

**What I learned:**
- How a JWT is built: header + claims + signature, Base64URL-encoded. Anyone can *read* the
  claims; the signature only proves nobody *changed* them. Never put secrets in claims.
- `@PreAuthorize("hasRole('ADMIN')")` checks for the authority `ROLE_ADMIN`; the converter adds
  the `ROLE_` prefix.
- 401 = "who are you?" (not authenticated), 403 = "I know who you are, but no" (not allowed).

**Interview questions I can now answer:**
- Q: Where do you store the JWT in the browser and why?
  A: sessionStorage, sent in the Authorization header. It survives refresh but not closing the
  tab, and there's no CSRF risk because it's not a cookie. The risk is XSS, so tokens are short
  (1 hour). The better version is a short access token in memory plus a refresh token in an
  HttpOnly SameSite cookie.
- Q: How would you log someone out before their token expires?
  A: With pure stateless JWTs you can't. Options: keep tokens short, keep a deny-list of token
  ids (`jti`) in Redis checked on each request, or store a "tokens valid after" timestamp per user.
- Q: Why BCrypt and not SHA-256 for passwords?
  A: SHA-256 is designed to be fast, so attackers can try billions of guesses per second.
  BCrypt is deliberately slow, has a tunable cost factor and a built-in salt.

## 2026-11-14 — M1: Walking skeleton

**Built:** Spring Boot 3.5 API on Java 21 with `/api/v1/ping` and Actuator health, PostgreSQL
(pgvector image) with Flyway, React + TypeScript + Vite + Tailwind web app with an API status
badge, Dockerfiles for both, Docker Compose for the full stack, GitHub Actions CI.

**Decisions and why:**
- Built the pipeline before any features (walking skeleton). Every later feature goes through
  tests, CI and Docker from day one, instead of trying to bolt them on at the end.
- Injected a `Clock` bean instead of calling `Instant.now()`. Tests can freeze time, which
  matters a lot later for alert rules like "error rate > 5% for 5 minutes".
- Used the `pgvector/pgvector:pg16` image from the start, even though embeddings come in M6,
  so I never have to switch databases halfway through.
- Testcontainers instead of H2 for integration tests: H2 is not PostgreSQL, so migrations
  could pass in tests and fail in production. Testcontainers runs the same image as Compose.
- Vite proxy in dev and nginx proxy in Docker, so the browser always sees one origin and the
  backend needs no CORS config.
- `/api/v1/ping` (no DB) and `/actuator/health` (checks DB) are separate: one answers "is the
  process alive?", the other "can it actually serve requests?".

**What broke / what confused me:**
- Testcontainers failed because Docker Desktop wasn't running. Integration tests need Docker.
- `@WebMvcTest` didn't find my `Clock` bean: test slices only load web components, not normal
  `@Configuration` classes. Fixed by giving the test its own fixed clock with `@TestConfiguration`,
  which also made the timestamp assertion exact.
- Docker Engine 29 rejects the API version used by older Testcontainers, so the version is
  pinned to 1.21.4 in the pom.

**What I learned:**
- Multi-stage Docker builds: the JDK and Maven are only in the build stage, the final image
  has just a JRE and the jar, and runs as a non-root user.
- Copying `pom.xml` before `src` in the Dockerfile caches dependency downloads between builds.
- JaCoCo can fail the build when coverage drops below a limit (80% here).

**Interview questions I can now answer:**
- Q: Why Testcontainers instead of an in-memory database?
  A: An in-memory DB behaves differently from PostgreSQL (SQL dialect, extensions like
  pgvector). Testcontainers runs the real database in Docker, so if migrations pass in tests
  they pass in production.
- Q: What's the difference between liveness and readiness?
  A: Liveness means the process is running; if it fails, restart it. Readiness means it can
  serve traffic (for example the database is reachable); if it fails, stop sending requests
  but don't restart.

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
