# ADR-0003: Stateless JWT authentication with role claims

- **Status:** Accepted
- **Date:** 2026-11-15

## Context

The React app calls a REST API. We need login, three roles (Admin, SRE, Developer) and
role checks on endpoints. Options considered:

| Option | Pros | Cons |
|---|---|---|
| Server sessions (cookie + session store) | Easy to revoke, cookie is HttpOnly | Needs a shared session store (Redis) once there is more than one instance; CSRF protection needed |
| **Self-signed JWT (HS256)** | Stateless, any instance can validate, standard Spring Security support | Can't revoke a token before it expires; token readable by JavaScript if stored in the browser |
| External identity provider (Keycloak, Cognito) | Production-grade, SSO | Another service to run and configure; overkill for v1 |

## Decision

- The API issues **HS256-signed JWTs** using Spring Security's `JwtEncoder` (Nimbus) and
  validates them with the OAuth2 **resource server** support. No hand-written token parsing.
- Claims: `sub` (user id), `email`, `name`, `roles`, `iss`, `iat`, `exp`. Lifetime: **1 hour**.
- `roles` maps to Spring authorities (`ROLE_SRE`) and endpoints use `@PreAuthorize`.
- Login failures return the same generic 401 for an unknown email, wrong password or disabled
  account, and the unknown-email path still runs a BCrypt check so response time doesn't reveal
  which emails exist.
- The secret comes from `JWT_SECRET`; startup fails if it is shorter than 32 bytes, and the
  `prod` profile refuses the development default.
- The browser keeps the token in **sessionStorage** and sends it in the `Authorization` header.
  Because it's not a cookie, CSRF protection isn't needed.

## Consequences

- Any number of API instances can validate tokens without shared state.
- A stolen token is valid until it expires (max 1 hour). Mitigations: short lifetime, React
  escapes output by default (XSS is the main way to steal it), no token in URLs or logs.
- **Later improvement:** short-lived access token (15 min) plus a refresh token in an
  HttpOnly, SameSite=Strict cookie, with refresh-token rotation and a revocation list.
- If another service ever needs to validate tokens, switch to RS256 so it only needs the
  public key.
