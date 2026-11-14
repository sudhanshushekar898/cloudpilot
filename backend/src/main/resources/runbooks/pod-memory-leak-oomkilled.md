# Pod memory leak and OOMKilled restarts

Applies to: Kubernetes workloads such as **payments-worker** and inventory-pod.
Owner: the service team.

## Symptoms

- Memory grows steadily over time and never drops after garbage collection.
- Logs show `GC overhead` warnings, then `OOMKilled (exit code 137)`.
- Availability drops as pods restart; errors appear in bursts around each restart.

## Verify

1. Check restarts and the last termination reason:
   `kubectl get pods -l app=<service>` and `kubectl describe pod <pod>` (look for `OOMKilled`).
2. Compare when the memory growth started with recent deployments. A new version that caches
   data in memory is a common cause.
3. Take a heap dump from one pod before it is killed (`jcmd <pid> GC.heap_dump`) to see what grows.

## Safe mitigation

- Roll back the release that introduced the growth, if there is one.
- As a stop-gap, schedule a rolling restart before pods reach the limit; this buys time, it does not fix anything.
- Raising the memory limit only delays the next OOMKill; use it only with a rollback plan.

## Long-term fixes

- Bound in-memory caches (size and TTL), or move them to Redis.
- Alert on memory above 90% of the limit for 3 minutes, before the OOMKill.
