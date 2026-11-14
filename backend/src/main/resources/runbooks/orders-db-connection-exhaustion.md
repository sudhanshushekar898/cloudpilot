# Orders DB connection exhaustion

Applies to: **orders-db** (RDS PostgreSQL) and its clients checkout-api and payments-worker.
Owner: Platform SRE.

## Symptoms

- orders-db logs `too many clients already` or `remaining connection slots are reserved`.
- checkout-api logs `HikariPool-1 - Connection is not available, request timed out`.
- checkout-api p95 latency above 2 s and error rate above 5%; payments-worker payment updates fail.
- orders-db CPU high while query throughput is flat or falling (connections waiting, not working).

## Verify

1. Check active connections against the limit:
   `SELECT count(*), state FROM pg_stat_activity GROUP BY state;` and `SHOW max_connections;`
2. Find which service holds the connections:
   `SELECT application_name, count(*) FROM pg_stat_activity GROUP BY 1 ORDER BY 2 DESC;`
3. Look for long-running or idle-in-transaction sessions:
   `SELECT pid, now() - xact_start AS age, state, query FROM pg_stat_activity WHERE state <> 'idle' ORDER BY age DESC LIMIT 20;`
4. Check whether a client recently scaled out (more pods = more pools = more connections).

## Safe mitigation

- If one client holds most connections, scale that client **down** or restart its pods so pools
  shrink. Restart one pod at a time and watch error rate.
- Terminate only sessions that are `idle in transaction` for more than 5 minutes, after
  confirming they are not migrations: `SELECT pg_terminate_backend(pid) ...`.
- Do **not** raise `max_connections` during the incident: it needs a reboot on RDS and more
  connections usually make CPU contention worse.

## Long-term fixes

- Put PgBouncer in transaction pooling mode in front of orders-db.
- Size HikariCP pools so that `pods x maximumPoolSize` stays below 80% of `max_connections`.
- Alert on connections above 80% of the limit for 5 minutes.
