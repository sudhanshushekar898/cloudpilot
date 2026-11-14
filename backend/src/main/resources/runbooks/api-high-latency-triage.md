# API high latency triage

Applies to: HTTP services (checkout-api, catalog-api) and the web-frontend that calls them.
Owner: on-call SRE.

## Symptoms

- p95 latency above 1 s for several minutes.
- web-frontend logs slow responses from one API.
- Timeouts appear in callers before errors appear in the slow service itself.

## Verify

1. Find the slowest hop: follow the dependency graph from the frontend downwards. The first
   resource that is slow while its own dependencies are healthy is the likely origin.
2. Check the dependencies of the slow service (databases, queues) for saturation: CPU, connection
   pools, message age.
3. Check recent deployments of the slow service **and** of the services it calls.
4. Rule out unrelated changes: a deployment to a service that is not on the slow request path
   (for example a CDN host change in catalog-api during a checkout slowdown) is usually not the cause.

## Safe mitigation

- Fix or roll back the origin, not the symptoms. Scaling a healthy API does not help when its
  database is saturated.
- Shed non-critical load (disable recommendation widgets) if checkout is affected.

## Long-term fixes

- Set timeouts and circuit breakers on every outbound call.
- Track latency per dependency, not just per endpoint.
