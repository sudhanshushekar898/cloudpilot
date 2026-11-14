# Bad deployment and rollback

Applies to: any service deployed by the CI pipeline (checkout-api, catalog-api, payments-worker).
Owner: the service team, with SRE support.

## Symptoms

- Error rate or availability changes sharply within minutes of a deployment.
- New exception types in the logs that did not exist before, for example `NullPointerException`
  or `500 Internal Server Error` on one endpoint.
- Only the newly deployed service is unhealthy at first; its callers show errors later.

## Verify

1. Compare the deployment time with the first alert. A deployment more than 30 minutes before the
   first error is a weak suspect.
2. Check whether the errors come from code paths changed in the release (release notes, diff).
3. Confirm the deployed service is the first resource with symptoms. If its dependency (database,
   queue) was unhealthy first, the deployment is probably not the cause.
4. Check that other deployments in the same window are unrelated to the failing path.

## Safe mitigation

- Roll back to the previous version through the pipeline:
  `kubectl rollout undo deployment/<service>` or re-run the last good pipeline.
- Prefer rollback over a hot fix while customers are impacted.
- After rollback, watch error rate and availability for 10 minutes before declaring recovery.

## Long-term fixes

- Canary releases: send 5% of traffic to the new version and roll back automatically on errors.
- Feature flags for risky changes such as switching payment providers.
- Add a contract test for the integration that broke.
