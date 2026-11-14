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
