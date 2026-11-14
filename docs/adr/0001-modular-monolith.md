# ADR-0001: Start as a modular monolith

- **Status:** Accepted
- **Date:** 2026-11-14

## Context

CloudPilot has several distinct areas: auth, resources, metrics, alerts, incidents, knowledge
base and AI. Splitting them into microservices from day one would add service discovery,
network failures between services, distributed transactions, several deploy pipelines and
several databases. The expected load (a few hundred metric samples per second from the
simulator, a handful of concurrent users) does not need any of that.

## Decision

Build **one Spring Boot application** with one PostgreSQL database, organised into modules
by package (`dev.cloudpilot.<module>`). Rules:

1. Each module exposes a small public API (a service interface and DTOs).
2. Other modules call only that public API, never another module's repositories or entities.
3. Modules communicate asynchronously through Spring application events where a direct call
   would create a cycle (for example `metrics` → `alerts` → `incidents`).
4. Boundaries are checked by an architecture test (ArchUnit) in CI.

## Consequences

- One build, one deploy, one database transaction when needed: faster to develop and debug.
- Boundaries are enforced by tests, not by the network, so they can be broken by accident;
  the ArchUnit test is what keeps them honest.
- If one area needs to scale on its own later (most likely alert ingestion), it can be
  extracted into its own service because its API is already isolated.
